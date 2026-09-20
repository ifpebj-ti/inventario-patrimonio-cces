package clp.inventory.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "im_sector")
public class Sector {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_sector_id")
    @SequenceGenerator(name = "im_sector_id", sequenceName = "im_sector_id", allocationSize = 1)
    private long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String code;

    @ManyToOne
    @JoinColumn(name = "id_organization", nullable = false)
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "id_parent_sector")
    private Sector parentSector;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Sector() {
    }

    public Sector(String name, String code, Organization organization, Sector parentSector) {
        this.name = name;
        this.code = code;
        this.organization = organization;
        this.parentSector = parentSector;
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

    public String code() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Organization organization() {
        return organization;
    }

    public Sector parentSector() {
        return parentSector;
    }

    public void setParentSector(Sector parentSector) {
        this.parentSector = parentSector;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }
}
