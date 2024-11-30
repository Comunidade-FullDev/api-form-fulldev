package com.fulldev.formulario.form.repositoryes;

import com.fulldev.formulario.form.model.entities.Form;
import com.fulldev.formulario.form.model.entities.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByForm(Form form);
}
