package clp.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "im_sector_allocation")
public class SectorAllocation {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "im_sector_allocation_id")
  @SequenceGenerator(
      name = "im_sector_allocation_id",
      sequenceName = "im_sector_allocation_id",
      allocationSize = 1)
  private long id;

  @ManyToOne
  @JoinColumn(name = "id_user", nullable = false)
  private User user;

  @ManyToOne
  @JoinColumn(name = "id_sector", nullable = false)
  private Sector sector;

  public SectorAllocation() {}

  public SectorAllocation(User user, Sector sector) {
    this.user = user;
    this.sector = sector;
  }

  public long id() {
    return id;
  }

  public User user() {
    return user;
  }

  public Sector sector() {
    return sector;
  }
}
