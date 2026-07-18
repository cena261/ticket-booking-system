package com.ticketapp.application.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EventMetadataShapeTest {

    private static List<String> componentNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    @Test
    void cachedTicketTypeCarriesNoStockField() {
        assertThat(componentNames(TicketTypeMetadata.class))
                .as("stock mutates ~30k/sec during a flash sale; a cached copy would contradict the Redis counter")
                .noneMatch(name -> name.contains("stock"));
    }

    @Test
    void cachedEventCarriesNoStockField() {
        assertThat(componentNames(EventMetadata.class))
                .noneMatch(name -> name.contains("stock"));
    }

    @Test
    void servedViewStillCarriesStockSoTheApiContractIsUnchanged() {
        assertThat(componentNames(TicketTypeView.class)).contains("stockavailable");
    }
}
