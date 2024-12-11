package com.fulldev.formulario.security.domain.controller;


import com.fulldev.formulario.security.domain.model.entity.UserRole;
import com.fulldev.formulario.security.domain.service.TokenService;
import com.fulldev.formulario.security.domain.dto.AuthDTO;
import com.fulldev.formulario.security.domain.dto.LoginResponseDTO;
import com.fulldev.formulario.security.domain.dto.RegisterDTO;
import com.fulldev.formulario.security.domain.model.entity.User;
import com.fulldev.formulario.security.domain.repository.UserRepository;
import com.fulldev.formulario.security.domain.service.EmailService;
import com.fulldev.formulario.security.domain.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthDTO authDTO) {
        try {
            User user = (User) userRepository.findByEmail(authDTO.email());

            if (user == null)
                return ResponseEntity.status(403).body("Não foi possível realizar o login do usuário. Existe algum campo obrigatório nulo ou vazio.");


            if (!user.isVerified())
                return ResponseEntity.status(403).body("Email do usuário não verificado.");


            var emailAndPassword = new UsernamePasswordAuthenticationToken(authDTO.email(), authDTO.password());
            var auth = this.authenticationManager.authenticate(emailAndPassword);

            var token = tokenService.generateToken((User) auth.getPrincipal());

            return ResponseEntity.ok(new LoginResponseDTO(token));
        } catch (Exception e) {
            return ResponseEntity.status(403).body("Login falhou: " + e.getMessage());
        }
    }


    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterDTO registerDTO) {
        try {
            if (this.userRepository.findByEmail(registerDTO.email()) != null)
                return ResponseEntity.badRequest().body("Usuário já existe");

            if (registerDTO.email() == null || !userService.passwordisValid(registerDTO.password()))
                return ResponseEntity.status(403).body("Não foi possível realizar o registro do usuário. Existe algum campo obrigatório nulo ou vazio.");

            String encryptedPassword = new BCryptPasswordEncoder().encode(registerDTO.password());

            User user = new User(registerDTO.email(), encryptedPassword, UserRole.ADMIN);

            String verificationToken = java.util.UUID.randomUUID().toString();
            user.setVerificationToken(verificationToken);

            String verificationLink = "http://localhost:8080/auth/verify?token=" + verificationToken;
            emailService.sendVerificationEmail(user.getUsername(), "Confirmação de Cadastro", verificationLink);

            this.userRepository.save(user);

            return ResponseEntity.ok("Usuário registrado. Verifique seu e-mail para ativar sua conta.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @GetMapping("/verify")
    public ResponseEntity verifyUser(@RequestParam String token) {
        User user = userRepository.findByVerificationToken(token);

        if (user == null) {
            return ResponseEntity.badRequest().body("Token inválido.");
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        emailService.sendVerificationEmail(user.getUsername(), "E-mail Verificado com Sucesso!", null);
        userRepository.save(user);

        return ResponseEntity.ok("Conta verificada com sucesso.");
    }

    @DeleteMapping(path = {"/delete"+"/{id}"})
    public ResponseEntity delete(@PathVariable String id){
        return userRepository.findById(id)
                .map(record -> {userRepository.deleteById(id);
                    return ResponseEntity.ok().body(record);
                }).orElse(ResponseEntity.notFound().build());
    }
}
