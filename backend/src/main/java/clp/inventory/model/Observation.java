package clp.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "im_observation")
public class Observation {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_observation_id")
    @SequenceGenerator(name = "im_observation_id", sequenceName = "im_observation_id", allocationSize = 1)
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
