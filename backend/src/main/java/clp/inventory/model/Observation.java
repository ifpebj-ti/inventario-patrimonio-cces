package clp.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "im_observation")
public class Observation {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String content;

    public Observation() {
    }

    public Observation(String content) {
        this.content = content;
    }

    public long id() {
        return id;
    }

    public String content() {
        return content;
    }

}
