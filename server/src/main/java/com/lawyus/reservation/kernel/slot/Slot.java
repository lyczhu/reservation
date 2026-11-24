package com.lawyus.reservation.kernel.slot;

import java.time.LocalTime;

public record Slot(LocalTime start, LocalTime end, SlotState state) {
}
