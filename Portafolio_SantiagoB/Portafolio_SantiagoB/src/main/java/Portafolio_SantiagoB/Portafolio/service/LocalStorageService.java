/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Portafolio_SantiagoB.Portafolio.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalStorageService {


    private final Path carpetaRaiz = Paths.get("imagenes");

    public String uploadImage(MultipartFile localFile, String folder, Integer id) throws IOException {
        String originalName = localFile.getOriginalFilename();
        String fileExtension = "";
        if (originalName != null && originalName.contains(".")) {
            fileExtension = originalName.substring(originalName.lastIndexOf("."));
        }

        String fileName = "img" + String.format("%014d", id) + fileExtension;

        Path carpeta = carpetaRaiz.resolve(folder);
        Files.createDirectories(carpeta);

        Files.copy(localFile.getInputStream(), carpeta.resolve(fileName),
                StandardCopyOption.REPLACE_EXISTING);

        return "/imagenes/" + folder + "/" + fileName + "?v=" + System.currentTimeMillis();
    }
}