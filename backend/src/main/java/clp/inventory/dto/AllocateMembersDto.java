package clp.inventory.dto;

import java.util.List;

/** Corpo de {@code PUT /sectors/{id}/members}: os usuários a alocar e o cargo de todos eles. */
public record AllocateMembersDto(List<Long> userIds, Long roleId) {}
