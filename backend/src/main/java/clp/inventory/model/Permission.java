package clp.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "im_permission")
public class Permission {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_permission_id")
    @SequenceGenerator(name = "im_permission_id", sequenceName = "im_permission_id", allocationSize = 1)
    private long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    private String description;

    public Permission() {
    }

    public Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
