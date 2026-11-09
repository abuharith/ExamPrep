package com.examprep.api.user;

import java.util.UUID;

public interface CurrentUserService {

    /** @return the id of the signed-in student, or throws if the session is not authenticated. */
    UUID requireCurrentUserId();
}
