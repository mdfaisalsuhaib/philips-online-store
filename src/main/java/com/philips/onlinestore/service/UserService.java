package com.philips.onlinestore.service;

import com.philips.onlinestore.dto.RegisterRequest;
import com.philips.onlinestore.dto.LoginRequest;
import com.philips.onlinestore.dto.LoginResponse;
import com.philips.onlinestore.dto.UserResponse;

public interface UserService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
