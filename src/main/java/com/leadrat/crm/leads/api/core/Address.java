package com.leadrat.crm.leads.api.core;

import com.leadrat.crm.leads.api.util.Validations;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
@Embeddable
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class Address implements Serializable {

    private String line1;
    private String line2;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private Coordinate point;

    public Address(String line1, String line2, String city, String state, String country, String pincode) {
        this(line1, line2, city, state, country, pincode, null);
        if (pincode != null) {
            Validations.isValidPin(pincode);
        }
    }

    public static Address from(String line1, String line2, String city, String state, String country, String pincode) {
        return new Address(line1, line2, city, state, country, pincode);
    }

    @Getter
    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinate implements Serializable {
        private Double latitude;
        private Double longitude;
    }
}
