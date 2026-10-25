package com.telemed.notifications.application.port.out;

import com.telemed.notifications.application.exception.PatientContactUnavailableException;

import java.util.Optional;
import java.util.UUID;

/** Identity owns the patient's e-mail; notifications asks for it only while it sends one. */
public interface PatientContactPort {

    /**
     * @return the e-mail of an active patient, or empty for any other account
     * @throws PatientContactUnavailableException if identity cannot answer now
     */
    Optional<String> findEmail(UUID patientId);
}
