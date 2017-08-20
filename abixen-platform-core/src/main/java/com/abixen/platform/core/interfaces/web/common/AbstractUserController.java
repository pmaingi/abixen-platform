/**
 * Copyright (c) 2010-present Abixen Systems. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.abixen.platform.core.interfaces.web.common;

import com.abixen.platform.core.infrastructure.configuration.properties.AbstractPlatformResourceConfigurationProperties;
import com.abixen.platform.core.interfaces.converter.RoleToRoleDtoConverter;
import com.abixen.platform.core.interfaces.converter.UserToUserDtoConverter;
import com.abixen.platform.common.dto.FormErrorDto;
import com.abixen.platform.common.dto.FormValidationResultDto;
import com.abixen.platform.core.application.dto.RoleDto;
import com.abixen.platform.core.application.dto.UserDto;
import com.abixen.platform.core.application.form.UserChangePasswordForm;
import com.abixen.platform.core.application.form.UserForm;
import com.abixen.platform.core.application.form.UserRolesForm;
import com.abixen.platform.common.model.enumtype.UserLanguage;
import com.abixen.platform.core.domain.model.Role;
import com.abixen.platform.core.domain.model.User;
import com.abixen.platform.core.application.service.MailService;
import com.abixen.platform.core.application.service.RoleService;
import com.abixen.platform.core.application.service.SecurityService;
import com.abixen.platform.core.application.service.UserService;
import com.abixen.platform.common.util.ValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.LocaleUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public abstract class AbstractUserController {

    private final UserService userService;
    private final MailService mailService;
    private final RoleService roleService;
    private final SecurityService securityService;
    private final MessageSource messageSource;
    private final UserToUserDtoConverter userToUserDtoConverter;
    private final RoleToRoleDtoConverter roleToRoleDtoConverter;


    private final AbstractPlatformResourceConfigurationProperties platformResourceConfigurationProperties;

    public AbstractUserController(UserService userService,
                                  MailService mailService,
                                  RoleService roleService,
                                  SecurityService securityService,
                                  AbstractPlatformResourceConfigurationProperties platformResourceConfigurationProperties,
                                  MessageSource messageSource,
                                  UserToUserDtoConverter userToUserDtoConverter,
                                  RoleToRoleDtoConverter roleToRoleDtoConverter) {
        this.userService = userService;
        this.mailService = mailService;
        this.roleService = roleService;
        this.securityService = securityService;
        this.platformResourceConfigurationProperties = platformResourceConfigurationProperties;
        this.messageSource = messageSource;
        this.userToUserDtoConverter = userToUserDtoConverter;
        this.roleToRoleDtoConverter = roleToRoleDtoConverter;
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public UserDto getUser(@PathVariable Long id) {
        log.debug("getUser() - id: " + id);

        User user = userService.find(id);
        UserDto userDto = userToUserDtoConverter.convert(user);
        return userDto;
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public FormValidationResultDto createUser(@RequestBody @Valid UserForm userForm, BindingResult bindingResult) {
        log.debug("save() - userForm: " + userForm);

        if (bindingResult.hasErrors()) {
            List<FormErrorDto> formErrors = ValidationUtil.extractFormErrors(bindingResult);
            return new FormValidationResultDto(userForm, formErrors);
        }

        String userPassword = userService.generatePassword();
        User createdUser = userService.create(userForm, userPassword);
        userForm.setId(createdUser.getId());

        Map<String, String> params = new HashMap<>();
        params.put("email", createdUser.getUsername());
        params.put("password", userPassword);
        params.put("firstName", createdUser.getFirstName());
        params.put("lastName", createdUser.getLastName());
        params.put("accountActivationUrl", "http://localhost:8080/login#/?activation-key=" + createdUser.getHashKey());

        String subject = messageSource.getMessage("email.userAccountActivation.subject", null, LocaleUtils.toLocale(userForm.getSelectedLanguage().getSelectedLanguage().toLowerCase()));

        //TODO
        mailService.sendMail(createdUser.getUsername(), params, MailService.USER_ACCOUNT_ACTIVATION_MAIL + "_" + userForm.getSelectedLanguage().getSelectedLanguage().toLowerCase(), subject);

        return new FormValidationResultDto(userForm);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<Boolean> deleteUser(@PathVariable("id") long id) {
        log.debug("delete() - id: " + id);
        userService.delete(id);
        return new ResponseEntity<Boolean>(Boolean.TRUE, HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
    public FormValidationResultDto updateUser(@PathVariable Long id, @RequestBody @Valid UserForm userForm, BindingResult bindingResult) {
        log.debug("update() - id: " + id + ", userForm: " + userForm);

        if (bindingResult.hasErrors()) {
            List<FormErrorDto> formErrors = ValidationUtil.extractFormErrors(bindingResult);
            return new FormValidationResultDto(userForm, formErrors);
        }

        UserForm userFormResult = userService.update(userForm);
        return new FormValidationResultDto(userFormResult);
    }

    @RequestMapping(value = "/{id}/avatar/{hash}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Long id, @PathVariable String hash) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(platformResourceConfigurationProperties.getImageLibraryDirectory() + "/user-avatar/" + hash);
        } catch (FileNotFoundException e) {
            in = new FileInputStream(platformResourceConfigurationProperties.getImageLibraryDirectory() + "/user-avatar/avatar.png");
        }
        byte[] b = IOUtils.toByteArray(in);

        in.close();

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity<byte[]>(b, headers, HttpStatus.CREATED);

    }

    @RequestMapping(value = "/{id}/avatar", method = RequestMethod.POST)
    public User updateUserAvatar(@PathVariable Long id, @RequestParam("avatarFile") MultipartFile avatarFile) throws IOException {
        return userService.changeAvatar(id, avatarFile);
    }

    @RequestMapping(value = "/{id}/roles", method = RequestMethod.GET)
    public UserRolesForm getUserRoles(@PathVariable Long id) {
        log.debug("getUserRoles() - id: " + id);

        User user = userService.find(id);
        List<Role> allRoles = roleService.findAll();

        UserDto userDto = userToUserDtoConverter.convert(user);
        List<RoleDto> allRolesDto = roleToRoleDtoConverter.convertToList(allRoles);

        UserRolesForm userRolesForm = new UserRolesForm(userDto, allRolesDto);

        return userRolesForm;
    }

    @RequestMapping(value = "/{id}/password", method = RequestMethod.POST)
    public FormValidationResultDto changeUserPassword(@PathVariable Long id, @RequestBody @Valid UserChangePasswordForm userChangePasswordForm, BindingResult bindingResult) {
        log.debug("changePassword() - id: " + id + ", changeUserPasswordForm: " + userChangePasswordForm);

        if (bindingResult.hasErrors()) {
            List<FormErrorDto> formErrors = ValidationUtil.extractFormErrors(bindingResult);
            return new FormValidationResultDto(userChangePasswordForm, formErrors);
        }

        User user = userService.find(id);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        UserChangePasswordForm userChangePasswordFormResult;

        try {
            userChangePasswordFormResult = userService.changePassword(user, userChangePasswordForm);

        } catch (UsernameNotFoundException e) {
            List<FormErrorDto> formErrors = new ArrayList<>();
            FormErrorDto formErrorDto = new FormErrorDto("currentPassword", "WrongPassword", "Wrong password", userChangePasswordForm.getCurrentPassword());
            formErrors.add(formErrorDto);
            return new FormValidationResultDto(userChangePasswordForm, formErrors);
        }

        Map<String, String> params = new HashMap<>();
        params.put("email", user.getUsername());
        params.put("password", userChangePasswordForm.getNewPassword());
        params.put("firstName", user.getFirstName());
        params.put("lastName", user.getLastName());

        String subject = messageSource.getMessage("email.userPasswordChanged.subject", null, LocaleUtils.toLocale(user.getSelectedLanguage().getSelectedLanguage().toLowerCase()));

        mailService.sendMail(user.getUsername(), params, MailService.USER_PASSWORD_CHANGE_MAIL + "_" + user.getSelectedLanguage().getSelectedLanguage().toLowerCase(), subject);

        return new FormValidationResultDto(userChangePasswordFormResult);
    }

    @RequestMapping(value = "/selected-language/{selectedLanguage}", method = RequestMethod.PUT)
    public ResponseEntity<UserLanguage> updateSelectedLanguage(@PathVariable UserLanguage selectedLanguage) {
        log.debug("updateSelectedLanguage() for logged user : " + selectedLanguage);
        UserLanguage updatedSelectedLanguage = userService.updateSelectedLanguage(securityService.getAuthorizedUser().getId(), selectedLanguage);
        return new ResponseEntity(updatedSelectedLanguage, HttpStatus.OK);
    }
}