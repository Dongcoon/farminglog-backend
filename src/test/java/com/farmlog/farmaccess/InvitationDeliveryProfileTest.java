package com.farmlog.farmaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.farmlog.common.tenant.FarmAccessGuard;
import com.farmlog.farm.mapper.FarmMapper;
import com.farmlog.farmaccess.mapper.FarmAccessMapper;
import com.farmlog.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class InvitationDeliveryProfileTest {
  @Test
  void loggingDeliveryIsAvailableOnlyInNonProductionProfiles() {
    try (var local = new AnnotationConfigApplicationContext()) {
      local.getEnvironment().setActiveProfiles("local");
      local.register(LoggingInvitationDelivery.class);
      local.refresh();
      assertThat(local.getBeansOfType(InvitationDelivery.class)).hasSize(1);
    }
    try (var prod = new AnnotationConfigApplicationContext()) {
      prod.getEnvironment().setActiveProfiles("prod");
      prod.register(LoggingInvitationDelivery.class);
      prod.refresh();
      assertThat(prod.getBeansOfType(InvitationDelivery.class)).isEmpty();
    }
  }

  @Test
  void productionFailsClosedWhenRealDeliveryBeanIsMissing() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().setActiveProfiles("prod");
      context.register(LoggingInvitationDelivery.class, FarmAccessService.class);
      context.registerBean(FarmAccessMapper.class, () -> mock(FarmAccessMapper.class));
      context.registerBean(FarmAccessGuard.class, () -> mock(FarmAccessGuard.class));
      context.registerBean(FarmMapper.class, () -> mock(FarmMapper.class));
      context.registerBean(UserMapper.class, () -> mock(UserMapper.class));
      context.registerBean(ObjectMapper.class, () -> new ObjectMapper());

      assertThatThrownBy(context::refresh)
          .isInstanceOf(UnsatisfiedDependencyException.class)
          .hasMessageContaining("InvitationDelivery");
    }
  }
}
