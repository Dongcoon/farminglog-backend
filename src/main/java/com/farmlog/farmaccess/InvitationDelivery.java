package com.farmlog.farmaccess;

public interface InvitationDelivery {
  void deliver(String email, String rawToken);
}
