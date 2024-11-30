package com.fulldev.formulario.form.controller;

import com.fulldev.formulario.form.dto.AnswerDTO;
import com.fulldev.formulario.form.dto.FormDTO;
import com.fulldev.formulario.form.dto.QuestionDTO;
import com.fulldev.formulario.form.model.entities.Answer;
import com.fulldev.formulario.form.model.entities.Form;
import com.fulldev.formulario.form.model.entities.Question;
import com.fulldev.formulario.form.repositoryes.AnswerRepository;
import com.fulldev.formulario.form.repositoryes.FormRepository;
import com.fulldev.formulario.form.repositoryes.QuestionRepository;
import com.fulldev.formulario.security.domain.model.entity.User;
import com.fulldev.formulario.security.domain.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;

    private static final String BASE_URL = "http://localhost:8080/api/forms/public/";

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
            question.setText(questionDTO.text());
            question.setType(questionDTO.type());
            questionRepository.save(question);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(form);
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<?> publishForm(@PathVariable Long id, Principal principal) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form not found"));

        if (!form.getCreatedBy().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not the owner of this form.");
        }

        form.setIsPublished(true);
        formRepository.save(form);

        String link = BASE_URL + id;
        return ResponseEntity.ok(Map.of("message", "Form published successfully", "link", link));
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<Form> getPublicForm(@PathVariable Long id) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form not found"));

        if (!form.getIsPublished()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        return ResponseEntity.ok(form);
    }

    @PostMapping("/{id}/answers")
    public ResponseEntity<?> answerForm(@PathVariable Long id, @RequestBody List<AnswerDTO> answersDTO) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form not found"));

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

        return ResponseEntity.status(HttpStatus.CREATED).body("Response submitted successfully.");
    }

    @GetMapping("/{id}/answers")
    public ResponseEntity<List<Answer>> getFormAnswers(@PathVariable Long id, Principal principal) {
        Form form = formRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Form not found"));

        if (!form.getCreatedBy().equals(principal.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        List<Answer> answers = answerRepository.findByForm(form);
        return ResponseEntity.ok(answers);
    }
}
