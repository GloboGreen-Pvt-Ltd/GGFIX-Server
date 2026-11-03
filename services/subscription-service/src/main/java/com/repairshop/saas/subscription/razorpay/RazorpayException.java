package com.repairshop.saas.subscription.razorpay;

/** A failed Razorpay API call. The message never contains credentials. */
public class RazorpayException extends RuntimeException {

    private final int httpStatus;

    public RazorpayException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public RazorpayException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
