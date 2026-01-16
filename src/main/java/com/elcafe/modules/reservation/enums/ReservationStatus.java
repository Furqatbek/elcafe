package com.elcafe.modules.reservation.enums;

public enum ReservationStatus {
    PENDING,           // Awaiting confirmation or deposit
    CONFIRMED,         // Confirmed by restaurant or auto-confirmed
    DEPOSIT_PENDING,   // Waiting for deposit payment
    CANCELLED,         // Cancelled by customer or restaurant
    NO_SHOW,           // Customer didn't show up
    COMPLETED,         // Reservation fulfilled
    SEATED             // Customer arrived and seated
}
