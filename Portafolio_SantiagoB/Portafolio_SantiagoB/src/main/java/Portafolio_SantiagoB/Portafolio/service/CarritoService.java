/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Portafolio_SantiagoB.Portafolio.service;

import Portafolio_SantiagoB.Portafolio.domain.*;
import Portafolio_SantiagoB.Portafolio.repository.FacturaRepository;
import Portafolio_SantiagoB.Portafolio.repository.ProductoRepository;
import Portafolio_SantiagoB.Portafolio.repository.VentaRepository;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarritoService {

    private static final String ATTRIBUTE_CARRITO = "carrito";

    private final ProductoRepository productoRepository;
    private final FacturaRepository facturaRepository;
    private final VentaRepository ventaRepository;

    public CarritoService(ProductoRepository productoRepository, FacturaRepository facturaRepository, VentaRepository ventaRepository) {
        this.productoRepository = productoRepository;
        this.facturaRepository = facturaRepository;
        this.ventaRepository = ventaRepository;
    }

    // --- 1. Gestión de Sesión ---
    public List<Item> obtenerCarrito(HttpSession session) {
        @SuppressWarnings("unchecked")
        List<Item> carrito = (List<Item>) session.getAttribute(ATTRIBUTE_CARRITO);
        if (carrito == null) {
            carrito = new ArrayList<>();
        }
        return carrito;
    }

    public void guardarCarrito(HttpSession session, List<Item> carrito) {
        session.setAttribute(ATTRIBUTE_CARRITO, carrito);
    }

    public void agregarProducto(List<Item> carrito, Integer idProducto) {
        // 1. Buscar el producto en BD
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado."));

        // 2. Buscar si el item ya existe en el carrito
        Optional<Item> itemExistente = carrito.stream()
                .filter(i -> i.getProducto().getIdProducto().equals(idProducto))
                .findFirst();

        int cantidad = 1;

        if (itemExistente.isPresent()) {
            Item item = itemExistente.get();
            int nuevaCantidad = item.getCantidad() + cantidad;

            // 3. (CRÍTICO) Validación de Stock
            if (nuevaCantidad > producto.getExistencias()) {
                throw new RuntimeException("Stock insuficiente para agregar " + cantidad + " unidades.");
            }
            item.setCantidad(nuevaCantidad);
        } else {
            // 4. (Nuevo Item) Validación de Stock
            if (cantidad > producto.getExistencias()) {
                throw new RuntimeException("Stock insuficiente para agregar " + cantidad + " unidades.");
            }

            // 5. Crear y añadir nuevo Item
            Item nuevoItem = new Item();
            nuevoItem.setProducto(producto);
            nuevoItem.setCantidad(cantidad);
            nuevoItem.setPrecioHistorico(producto.getPrecio()); // Capturar precio actual
            carrito.add(nuevoItem);
        }
    }

    public Item buscarItem(List<Item> carrito, Integer idProducto) {
        if (carrito == null) {
            return null;
        }
        return carrito.stream()
                .filter(item -> item.getProducto().getIdProducto().equals(idProducto)) // Filtra por el ID
                .findFirst()                                                          // Obtiene el primero
                .orElse(null);                                                        // null si no lo encuentra
    }

    public void eliminarItem(List<Item> carrito, Integer idProducto) {
        // removeIf elimina los que cumplan la condición
        carrito.removeIf(item -> item.getProducto().getIdProducto().equals(idProducto));
    }

    public void actualizarCantidad(List<Item> carrito, Integer idProducto, int nuevaCantidad) {
        if (nuevaCantidad <= 0) {
            eliminarItem(carrito, idProducto);
            return;
        }

        Optional<Item> itemExistente = carrito.stream()
                .filter(i -> i.getProducto().getIdProducto().equals(idProducto))
                .findFirst();

        if (itemExistente.isPresent()) {
            Item item = itemExistente.get();
            Producto producto = item.getProducto();

            if (nuevaCantidad > producto.getExistencias()) {
                throw new RuntimeException("No hay suficiente stock disponible.");
            }
            item.setCantidad(nuevaCantidad);
        }
    }

    public int contarUnidades(List<Item> carrito) {
        if (carrito == null || carrito.isEmpty()) {
            return 0;
        }
        return carrito.stream()
                .mapToInt(Item::getCantidad) // Toma la cantidad de cada Item
                .sum();                      // Las suma todas
    }

    public BigDecimal calcularTotal(List<Item> carrito) {
        // Sumar todos los subtotales de la lista
        return carrito.stream()
                .map(Item::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void limpiarCarrito(HttpSession session) {
        List<Item> carrito = obtenerCarrito(session);
        if (carrito != null) {
            carrito.clear();
        }
        guardarCarrito(session, carrito);
    }

    @Transactional
    public Factura procesarCompra(List<Item> carrito, Usuario usuario) {
        System.out.println("Se va a Procesar la Compra...");
        if (carrito == null || carrito.isEmpty()) {
            throw new RuntimeException("El carrito está vacío para procesar la compra.");
        }

        // 1. CREAR Y GUARDAR LA FACTURA
        Factura factura = new Factura();
        factura.setUsuario(usuario);
        factura.setFecha(LocalDateTime.now());
        factura.setTotal(calcularTotal(carrito));
        factura.setEstado(EstadoFactura.Pagada);
        factura.setFechaCreacion(LocalDateTime.now());
        factura.setFechaModificacion(LocalDateTime.now());
        factura = facturaRepository.save(factura); // Se guarda para obtener el idFactura

        // 2. CREAR LAS LINEAS DE VENTA y ACTUALIZAR EXISTENCIAS
        for (Item item : carrito) {
            // a. Verificar existencias otra vez (doble chequeo)
            Producto producto = productoRepository.findById(item.getProducto().getIdProducto()).get();
            if (item.getCantidad() > producto.getExistencias()) {
                throw new RuntimeException("Fallo en la compra: El producto " + producto.getDescripcion() + " ya no tiene suficiente stock.");
            }

            // b. Crear la Venta (renglón de la factura)
            Venta venta = new Venta();
            venta.setFactura(factura);
            venta.setProducto(item.getProducto());
            venta.setPrecioHistorico(item.getPrecioHistorico());
            venta.setCantidad(item.getCantidad());
            venta.setFechaCreacion(LocalDateTime.now());
            venta.setFechaModificacion(LocalDateTime.now());
            ventaRepository.save(venta);

            // c. Restar las existencias del producto
            producto.setExistencias(producto.getExistencias() - item.getCantidad());
            productoRepository.save(producto);
        }

        // 3. Limpiar el carrito (lo hace el Controller)
        return factura;
    }
}