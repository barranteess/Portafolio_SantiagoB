/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Portafolio_SantiagoB.Portafolio.controller;

import Portafolio_SantiagoB.Portafolio.domain.Factura;
import Portafolio_SantiagoB.Portafolio.domain.Item;
import Portafolio_SantiagoB.Portafolio.domain.Usuario;
import Portafolio_SantiagoB.Portafolio.service.CarritoService;
import Portafolio_SantiagoB.Portafolio.service.FacturaService;
import Portafolio_SantiagoB.Portafolio.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CarritoController {

    private final CarritoService carritoService;
    private final UsuarioService usuarioService;
    private final FacturaService facturaService;

    public CarritoController(CarritoService carritoService, UsuarioService usuarioService, FacturaService facturaService) {
        this.carritoService = carritoService;
        this.usuarioService = usuarioService;
        this.facturaService = facturaService;
    }

    // --- 1. MOSTRAR EL CARRITO ---
    @GetMapping("/carrito/listado")
    public String listado(HttpSession session, Model model) {
        List<Item> carrito = carritoService.obtenerCarrito(session);
        model.addAttribute("carritoItems", carrito);
        model.addAttribute("totalCarrito", carritoService.calcularTotal(carrito));
        return "/carrito/listado";
    }

    // --- 2. AGREGAR PRODUCTO AL CARRITO (por AJAX) ---
    @PostMapping("/carrito/agregar")
    public ModelAndView agregar(
            @RequestParam("idProducto") Integer idProducto,
            HttpSession session,
            Model model) {
        try {
            // 1. Obtener el carrito de la sesión
            List<Item> carrito = carritoService.obtenerCarrito(session);

            // 2. Agregar el producto (cantidad = 1)
            carritoService.agregarProducto(carrito, idProducto);

            // 3. Guardar el carrito actualizado en la sesión
            carritoService.guardarCarrito(session, carrito);

            // 4. Recalcular y pasar los datos
            model.addAttribute("carritoTotal", carritoService.calcularTotal(carrito));
            model.addAttribute("listaItems", carrito);

            // 5. Retornar SOLO el pedacito de HTML del botón "Ver Carrito"
            return new ModelAndView("/carrito/fragmentos :: verCarrito", model.asMap());

        } catch (RuntimeException e) {
            // 6. Si hay error (ej: no hay existencias), se muestra el mensaje
            model.addAttribute("errorMensaje", e.getMessage());
            return new ModelAndView("/errores/fragmentos :: errorMensaje", model.asMap());
        }
    }

    // --- 3. ELIMINAR ITEM DEL CARRITO ---
    @PostMapping("/carrito/eliminar/{idProducto}")
    public String eliminarItem(
            @PathVariable("idProducto") Integer idProducto,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        List<Item> carrito = carritoService.obtenerCarrito(session);
        carritoService.eliminarItem(carrito, idProducto);
        carritoService.guardarCarrito(session, carrito);
        redirectAttributes.addFlashAttribute("mensaje", "Producto eliminado del carrito.");
        return "redirect:/carrito/listado";
    }

    // --- 4. PÁGINA PARA MODIFICAR LA CANTIDAD ---
    @GetMapping("/carrito/modificar/{idProducto}")
    public String modificar(
            @PathVariable("idProducto") Integer idProducto,
            HttpSession session,
            Model model) {
        List<Item> carrito = carritoService.obtenerCarrito(session);
        Item item = carritoService.buscarItem(carrito, idProducto);
        if (item == null) {
            // Si el producto no está en el carrito, regresa al listado
            return "redirect:/carrito/listado";
        }
        model.addAttribute("item", item);
        return "/carrito/modifica";
    }

    // --- 5. GUARDAR LA NUEVA CANTIDAD ---
    @PostMapping("/carrito/actualizar")
    public String actualizarCantidad(
            @RequestParam("producto.idProducto") Integer idProducto,
            @RequestParam("cantidad") int nuevaCantidad,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            List<Item> carrito = carritoService.obtenerCarrito(session);
            carritoService.actualizarCantidad(carrito, idProducto, nuevaCantidad);
            carritoService.guardarCarrito(session, carrito);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/carrito/listado";
    }

    // --- 6. PROCESAR LA COMPRA (FACTURAR) ---
    @GetMapping("/facturar/carrito")
    public String facturarCarrito(HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            List<Item> carrito = carritoService.obtenerCarrito(session);

            // Obtener el usuario que inició sesión
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Usuario usuario = usuarioService.getUsuarioPorUsername(username).get();

            // 1. Crear la factura (aquí se restan las existencias)
            Factura factura = carritoService.procesarCompra(carrito, usuario);

            // 2. Vaciar el carrito
            carritoService.limpiarCarrito(session);

            // 3. Pasar el número de factura a la siguiente página
            redirectAttributes.addFlashAttribute("idFactura", factura.getIdFactura());
            redirectAttributes.addFlashAttribute("mensaje", "Compra procesada con éxito. Factura Nro: " + factura.getIdFactura());

            // 4. Ir a ver la factura
            return "redirect:/carrito/verFactura";

        } catch (RuntimeException e) {
            // Errores: carrito vacío, sin existencias, etc.
            redirectAttributes.addFlashAttribute("error", "Error al procesar la compra: " + e.getMessage());
            return "redirect:/carrito/listado";
        }
    }

    // --- 7. MOSTRAR LA FACTURA ---
    @GetMapping("/carrito/verFactura")
    public String verFactura(Model model) {
        // El idFactura llega como Flash Attribute (desde facturarCarrito)
        Integer idFactura = (Integer) model.getAttribute("idFactura");
        if (idFactura == null) {
            // Si no hay factura, regresar a la página principal
            return "redirect:/";
        }
        // Obtener la factura COMPLETA (con usuario, ventas y productos)
        Factura factura = facturaService.getFacturaConVentas(idFactura);
        model.addAttribute("factura", factura);
        return "/carrito/verFactura";
    }
}