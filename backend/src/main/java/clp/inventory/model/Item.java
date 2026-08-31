package clp.inventory.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "im_item")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String code;

    private String name;

    private String description;

    // Valor em centavos, para evitar erros de arredondamento de ponto flutuante.
    private long price;

    private String locale;

    @Column(nullable = false)
    private String qrCode;

    private String responsible;

    @Column(nullable = false, updatable = false, unique = true)
    private boolean isValid = false;

    private LocalDateTime validatedAt;

    @ManyToOne
    @JoinColumn(name = "id_inventory")
    private Inventory inventory;

    @OneToMany(
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item", nullable = false)
    private List<Observation> observations = new ArrayList<>();

    public Item() {
    }

    public Item(String code, String name, String description, long price, String locale, String responsible) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.locale = locale;
        generateQrCode();
        this.responsible = responsible;
    }

    public long id() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean isValid() {
        return isValid;
    }

    public long price() {
        return price;
    }

    public String responsible() {
        return responsible;
    }

    public String locale() {
        return locale;
    }

    public String qrCode() {
        return qrCode;
    }

    public List<Observation> observations() {
        return observations;
    }

    public String getNotes() {
        if (this.observations == null || this.observations.isEmpty()) {
            return null;
        }
        return this.observations.stream()
                .map(Observation::content)
                .collect(Collectors.joining("\n"));
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void addObservation(Observation observation) {
        if (this.observations == null) {
            this.observations = new ArrayList<>();
        }
        observations.add(observation);
    }

    @PrePersist
    private void generateQrCode() {
        if (this.qrCode == null) {
            this.qrCode = UUID.randomUUID().toString();
        }
    }
}
