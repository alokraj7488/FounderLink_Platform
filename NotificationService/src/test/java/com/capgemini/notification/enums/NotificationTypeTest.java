package com.capgemini.notification.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeTest {

    @Test
    void testEnumValues() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(NotificationType.valueOf(type.name())).isEqualTo(type);
        }
    }
}
