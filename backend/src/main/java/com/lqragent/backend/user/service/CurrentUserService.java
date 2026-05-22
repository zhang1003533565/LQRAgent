package com.lqragent.backend.user.service;

import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User requireUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw BusinessException.unauthorized("未登录");
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
    }

    public Long requireUserId(UserDetails userDetails) {
        return requireUser(userDetails).getId();
    }
}
