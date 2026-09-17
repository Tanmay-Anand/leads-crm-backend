package com.leadrat.crm.leads.api.util;

import com.leadrat.crm.leads.api.exception.LeadratException;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.HttpStatus;

import java.util.regex.Pattern;

/**
 * Field-format checks shared across modules. Every {@code isValidX} is a no-op on null, so
 * callers can run it over optional fields without a null guard of their own.
 */
public final class Validations {

    /** Indian mobile, 10 digits, optional +91 prefix. */
    public static final Pattern mobilePattern =
            Pattern.compile("^(\\+91[\\s\\-]?)?[6-9]\\d{9}$", Pattern.CASE_INSENSITIVE);

    /** Minimal international fallback for non-Indian numbers. */
    public static final Pattern internationalMobilePattern =
            Pattern.compile("^\\+?[0-9]{6,15}$", Pattern.CASE_INSENSITIVE);

    public static final Pattern panPattern = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern aadhaarPattern = Pattern.compile("^[2-9]{1}[0-9]{11}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern pinPattern = Pattern.compile("^[1-9]{1}[0-9]{5}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern ifscPattern = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern gstInPattern =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[A-Z0-9]{3}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern reraPattern =
            Pattern.compile("^[A-Z0-9][A-Z0-9/\\.\\- ]{3,99}$", Pattern.CASE_INSENSITIVE);

    private Validations() {
    }

    public static String isValidMobile(String mobile) {
        if (mobile != null) {
            String normalized = mobile.replaceAll("[\\s-]", "");
            boolean isIndian = mobilePattern.matcher(mobile).matches() || mobilePattern.matcher(normalized).matches();

            if (mobile.startsWith("+91") && !isIndian) {
                throw new LeadratException(String.format("Invalid phone number: %s", mobile), HttpStatus.BAD_REQUEST);
            }

            boolean isInternational = internationalMobilePattern.matcher(mobile).matches()
                    || internationalMobilePattern.matcher(normalized).matches();

            if (!isIndian && !isInternational) {
                throw new LeadratException(String.format("Invalid phone number: %s", mobile), HttpStatus.BAD_REQUEST);
            }
        }
        return mobile;
    }

    public static String isValidEmail(String email) {
        if (email != null && !EmailValidator.getInstance().isValid(email)) {
            throw new LeadratException(String.format("Invalid email address: %s", email), HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    public static String isValidPan(String pan) {
        if (pan != null && !panPattern.matcher(pan).find()) {
            throw new LeadratException(String.format("Invalid PAN number: %s", pan), HttpStatus.BAD_REQUEST);
        }
        return pan;
    }

    public static String isValidAadhaar(String aadhaar) {
        if (aadhaar != null && !aadhaarPattern.matcher(aadhaar).find()) {
            throw new LeadratException(String.format("Invalid Aadhaar number: %s", aadhaar), HttpStatus.BAD_REQUEST);
        }
        return aadhaar;
    }

    public static String isValidPin(String pin) {
        if (pin != null && !pinPattern.matcher(pin).find()) {
            throw new LeadratException(String.format("Invalid PIN code: %s", pin), HttpStatus.BAD_REQUEST);
        }
        return pin;
    }

    public static String isValidIfsc(String ifsc) {
        if (ifsc != null && !ifscPattern.matcher(ifsc).find()) {
            throw new LeadratException(String.format("Invalid IFSC code: %s", ifsc), HttpStatus.BAD_REQUEST);
        }
        return ifsc;
    }

    public static String isValidGstIn(String gstIn) {
        if (gstIn != null && !gstInPattern.matcher(gstIn).find()) {
            throw new LeadratException(String.format("Invalid GST number: %s", gstIn), HttpStatus.BAD_REQUEST);
        }
        return gstIn;
    }

    public static String isValidRera(String rera) {
        if (rera != null && !reraPattern.matcher(rera).matches()) {
            throw new LeadratException(String.format("Invalid RERA number: %s", rera), HttpStatus.BAD_REQUEST);
        }
        return rera;
    }
}
