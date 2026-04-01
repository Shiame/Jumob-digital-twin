package com.gama_adapter.gama_adapter.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/traffic")
public class GamaTrafficController {

    @PostMapping
    public ResponseEntity<String> receiveTrafficData(@RequestBody String trafficData) {
        
       
        return ResponseEntity.ok("Données reçues par l'Adaptateur avec succès !");
    }
}
