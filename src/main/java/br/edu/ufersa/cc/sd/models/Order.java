package br.edu.ufersa.cc.sd.models;

import java.io.Serializable;
import java.time.LocalDateTime;

import br.edu.ufersa.cc.sd.utils.JsonUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order implements Serializable {

    @Id
    @GeneratedValue
    private Long code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    public String toString() {
        return JsonUtils.toJson(this);
    }

}
