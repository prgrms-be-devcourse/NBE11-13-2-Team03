package com.team3.gudit.auth.oauth2;

import java.util.Map;

public interface OAuth2UserInfo {

    Map<String, Object> attributes();

    Long id();

    String email();

    String name();

    String imageUrl();
}

