package com.farmlog.farmaccess.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvitationRow {
  private Long id, organizationId, farmId, invitedBy, acceptedUserId, version;
  private String email, role, inviteTokenHash, status, clientRequestId, invitedByName;
  private LocalDateTime invitedAt, respondedAt, expiresAt;
}
