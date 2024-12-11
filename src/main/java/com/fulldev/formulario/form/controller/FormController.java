package com.fulldev.formulario.form.controller;

import com.fulldev.formulario.form.dto.AnswerDTO;
import com.fulldev.formulario.form.dto.FormDTO;
import com.fulldev.formulario.form.dto.QuestionDTO;
import com.fulldev.formulario.form.model.entities.Answer;
import com.fulldev.formulario.form.model.entities.Form;
import com.fulldev.formulario.form.model.entities.FormHasLogin;
import com.fulldev.formulario.form.model.entities.Question;
import com.fulldev.formulario.form.repositoryes.AnswerRepository;
import com.fulldev.formulario.form.repositoryes.FormRepository;
import com.fulldev.formulario.form.repositoryes.QuestionRepository;
import com.fulldev.formulario.form.service.FormService;
import com.fulldev.formulario.security.domain.dto.LoginResponseDTO;
import com.fulldev.formulario.security.domain.dto.RegisterDTO;
import com.fulldev.formulario.security.domain.model.entity.User;
import com.fulldev.formulario.security.domain.model.entity.UserRole;
import com.fulldev.formulario.security.domain.repository.UserRepository;
import com.fulldev.formulario.security.domain.service.TokenService;
import com.fulldev.formulario.security.domain.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Arrays.*;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final FormService formService;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    private static final String BASE_URL = "http://localhost:3000/form/preview?";

    @PostMapping("/register")
    public ResponseEntity<?> registerUserToAnswerForm(@RequestBody @Valid RegisterDTO registerDTO) {
        try {
            
            if (userRepository.findByEmail(registerDTO.email()) != null) {
                return ResponseEntity.badRequest().body("Usuário já existe");
            }

            if (registerDTO.email() == null || !userService.passwordisValid(registerDTO.password())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Campos obrigatórios estão inválidos ou vazios.");
            }

            String encryptedPassword = new BCryptPasswordEncoder().encode(registerDTO.password());

            User newUser = new User(registerDTO.email(), encryptedPassword, UserRole.USER);
            
            userRepository.save(newUser);

            var emailAndPassword = new UsernamePasswordAuthenticationToken(registerDTO.email(), registerDTO.password());
            var auth = this.authenticationManager.authenticate(emailAndPassword);

            var token = tokenService.generateToken((User) auth.getPrincipal());

            return ResponseEntity.ok(new LoginResponseDTO(token));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao registrar usuário ou atualizar formulários", e);
        }
    }

    @PatchMapping("/default-settings/{standard}")
    public ResponseEntity defaultFormSettings(@PathVariable String standard, @RequestBody String password, Principal principal){
        try{
        String loggedUserEmail = principal.getName();
        List<Form> forms = formRepository.findByCreatedBy(loggedUserEmail);
            if (standard.equals(FormHasLogin.PRIVATE.toString().toLowerCase())) {
                for (Form form : forms) {
                    if (form.getIsPublished()) {
                        String idPublic = UUID.randomUUID().toString();
                        String link = BASE_URL + "logintype=" + standard + "&form=" + idPublic;

                        form.setFormHasLogin(FormHasLogin.PRIVATE);
                        form.setLink(link);
                        form.setIdPublic(idPublic);
                        formRepository.save(form);
                    }
                }
            }
            if (FormHasLogin.PASSWORD.getFormLoginType().toLowerCase().equals(standard)) {
                for (Form form : forms) {
                    if (form.getIsPublished()) {
                        String idPublic = UUID.randomUUID().toString();
                        String link = BASE_URL + "logintype=" + standard + "&form=" + idPublic;

                        form.setFormHasLogin(FormHasLogin.PASSWORD);
                        form.setAccessPassword(password);
                        form.setLink(link);
                        form.setIdPublic(idPublic);
                        formRepository.save(form);
                    }
                }
            }
            if (FormHasLogin.PUBLIC.getFormLoginType().toLowerCase().equals(standard)) {
                for (Form form : forms) {
                    if (form.getIsPublished()) {
                        String idPublic = UUID.randomUUID().toString();
                        String link = BASE_URL + "logintype=" + standard + "&form=" + idPublic;

                        form.setFormHasLogin(FormHasLogin.PUBLIC);
                        form.setLink(link);
                        form.setIdPublic(idPublic);
                        formRepository.save(form);
                    }
                }
            }

            return ResponseEntity.ok().body("configurações do formulário alteradas com sucesso");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @PostMapping
    public ResponseEntity<Form> createForm(@RequestBody @Valid FormDTO formDTO, Principal principal) {
        User user = (User) userRepository.findByEmail(principal.getName());

        Form form = new Form();
        form.setTitle(formDTO.title());
        form.setDescription(formDTO.description());
        form.setCreatedBy(user.getUsername());
        form.setIsPublished(false);
        formRepository.save(form);

        for (QuestionDTO questionDTO : formDTO.questions()) {
            Question question = new Question();
            question.setForm(form);
            question.setTitle(questionDTO.title());
            question.setOptions(questionDTO.options());
            question.setType(questionDTO.type());
            question.setRequired(questionDTO.required());
            question.setQuestionDescription(questionDTO.questionDescription());
            questionRepository.save(question);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(form);
    }

    @GetMapping("/my-forms")
    public ResponseEntity<List<Form>> getFormsByLoggedUser(Principal principal) {
        String email = principal.getName();

        User user = (User) userRepository.findByEmail(email);

        List<Form> forms = formRepository.findByCreatedBy(user.getEmail());

        return ResponseEntity.ok(forms);
    }

    @GetMapping("/my-forms/public")
    public ResponseEntity<List<Form>> getMyPublicForms(Principal principal){
        String email = principal.getName();

        List<Form> forms = formRepository.findByCreatedByAndIsPublishedTrue(email);

        return ResponseEntity.ok(forms);
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<?> publishForm(@PathVariable Long id, Principal principal) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));

        if (!form.getCreatedBy().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Formulário não encontrado");
        }
        String idPublic = UUID.randomUUID().toString();
        String link = BASE_URL + "logintype=" + form.getFormHasLogin().toString().toLowerCase() + "&form=" + idPublic;

        form.setIsPublished(true);
        form.setLink(link);
        form.setIdPublic(idPublic);
        formRepository.save(form);

        return ResponseEntity.ok(Map.of("message", "Form published successfully", "link", link));
    }

    @GetMapping("/public/{formHasLoginType}/{idPublic}")
    public ResponseEntity<?> getPublicForm(@PathVariable String formHasLoginType, @PathVariable String idPublic, @RequestParam(required = false) String password, Principal principal) {
        Form form = formRepository.findByidPublic(idPublic);

        if (form == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Formulário não encontrado. O link fornecido não existe");


        System.out.println(formHasLoginType);
        System.out.println(password.equals("123456789"));
        System.out.println(password);
        int views = form.getViews() + 1;
        form.setViews(views);
        formRepository.save(form);

        if (!form.getIsPublished())
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);

        if(password.isEmpty()) {
            if (formHasLoginType.equals(FormHasLogin.PUBLIC.toString().toLowerCase()))
                return ResponseEntity.ok(form);


            if (formHasLoginType.equals(FormHasLogin.PRIVATE.toString().toLowerCase())) {

                if (principal.getName().isEmpty())
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Autenticação necessária. Esse formulário é: " + form.getFormHasLogin());

                if (this.userRepository.findByEmail(principal.getName()) != null)
                    return ResponseEntity.ok(form);

            }
        }else {

            if (formHasLoginType.equals(FormHasLogin.PASSWORD.toString().toLowerCase())) {
                if (password.equals(form.getAccessPassword())) {
                    System.out.println("senha correta");
                    return ResponseEntity.ok(form);
                }

                if (!password.equals(form.getAccessPassword())) {
                    System.out.println("senha errada");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Autenticação necessária. Esse formulário é: " + form.getFormHasLogin());
                }
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Configuração do formulário inválida.");


    }

    @PostMapping("/{idPublic}/answers")
    public ResponseEntity<?> answerForm(@PathVariable String idPublic, @RequestBody List<AnswerDTO> answersDTO) {
        Form form = formRepository.findByidPublic(idPublic);

        if (!form.getIsPublished()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Form is not published yet.");
        }

        Answer answer = new Answer();
        answer.setForm(form);
        answer.setAnswers(
                answersDTO.stream()
                        .collect(Collectors.toMap(AnswerDTO::questionId, AnswerDTO::response))
        );
        answerRepository.save(answer);
        int countResponses = form.getResponsesCount() + 1;
        form.setResponsesCount(countResponses);
        formRepository.save(form);

        return ResponseEntity.status(HttpStatus.CREATED).body("Response submitted successfully.");
    }

    @GetMapping("/{id}/answers")
    public ResponseEntity<List<Answer>> getFormAnswers(@PathVariable Long id, Principal principal) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));

        if (!form.getCreatedBy().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        List<Answer> answers = answerRepository.findByForm(form);
        return ResponseEntity.ok(answers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Form> getFormById(@PathVariable Long id, Principal principal) {
        String userEmail = principal.getName();
        User user = (User) userRepository.findByEmail(userEmail);

        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));

        if (!form.getCreatedBy().equals(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        return ResponseEntity.ok(form);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Form> updateForm(@PathVariable Long id, @RequestBody FormDTO formDTO, Principal principal) {
        String email = principal.getName();
        User user = (User) userRepository.findByEmail(email);
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));

        if (!form.getCreatedBy().equals(user.getEmail())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        Form updatedForm = formService.updateForm(id, formDTO);

        return ResponseEntity.ok(updatedForm);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deleteFormById(@PathVariable Long id, Principal principal){
        String email = principal.getName();
        User user = (User) userRepository.findByEmail(email);
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulário não encontrado"));

        if (!form.getCreatedBy().equals(user.getEmail())){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        ResponseEntity deleteForm = formService.deleteFormById(id);
        return deleteForm;
    }


}
