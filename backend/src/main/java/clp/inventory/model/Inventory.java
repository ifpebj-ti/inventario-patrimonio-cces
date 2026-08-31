package clp.inventory.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "im_inventory")
public class Inventory {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @OneToMany(
            mappedBy = "inventory",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Item> items = new ArrayList<>();

    public Inventory() {
    }

    public User User() {
        return user;
    }

    public Inventory(String name, String description, User user) {
        this.name = name;
        this.description = description;
        this.user = user;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public List<Item> items() {
        return items;
    }

    public void addItem(Item item) {
        item.setInventory(this);
        items.add(item);
    }

    public void removeItem(Item item) {
        item.setInventory(this);
        items.remove(item);
    }

}
