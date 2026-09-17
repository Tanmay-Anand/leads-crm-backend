package com.leadrat.crm.leads.api.lead;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.extern.slf4j.Slf4j;

/**
 * The single source of truth for how a lead mobile number becomes an identity key.
 *
 * <p>A lead is unique per {@code (tenant, project, mobileNormalized)}. This class owns the
 * mobileNormalized half of that key, and it is the only place normalisation happens, so the
 * duplicate check and the write path can never disagree about what counts as the same person.
 *
 * <p>{@link #normalize} never throws. It is called from a JPA lifecycle callback, where an
 * exception would surface as an opaque persistence failure rather than a validation error, so an
 * unparseable number degrades to a digits-only form instead. Format validation belongs to
 * {@code Validations.isValidMobile}, which runs earlier and reports properly.
 */
@Slf4j
public final class LeadIdentity {

    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    /** Numbers arrive from an India-first product; an explicit country code always wins. */
    private static final String DEFAULT_REGION = "IN";

    private LeadIdentity() {
    }

    /**
     * Returns the E.164 form of the number, or a best-effort digits-only fallback.
     *
     * @param mobile      the number as the user typed it
     * @param countryCode the dialling code, such as +91. May be null.
     */
    public static String normalize(String mobile, String countryCode) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }

        String candidate = mobile.trim();
        if (countryCode != null && !countryCode.isBlank() && !candidate.startsWith("+")) {
            String dialCode = countryCode.trim();
            if (!dialCode.startsWith("+")) {
                dialCode = "+" + dialCode;
            }
            candidate = dialCode + candidate;
        }

        try {
            Phonenumber.PhoneNumber parsed = PHONE_UTIL.parse(candidate, DEFAULT_REGION);
            return PHONE_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            log.debug("Could not parse mobile {}, falling back to digits only: {}", mobile, e.getMessage());
            return digitsOnly(candidate);
        }
    }

    private static String digitsOnly(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
