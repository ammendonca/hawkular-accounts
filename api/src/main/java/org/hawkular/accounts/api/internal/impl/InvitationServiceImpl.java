/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.accounts.api.internal.impl;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.hawkular.accounts.api.InvitationService;
import org.hawkular.accounts.api.OrganizationMembershipService;
import org.hawkular.accounts.api.OrganizationService;
import org.hawkular.accounts.api.RoleService;
import org.hawkular.accounts.api.UserService;
import org.hawkular.accounts.api.internal.BoundStatements;
import org.hawkular.accounts.api.internal.NamedStatement;
import org.hawkular.accounts.api.model.HawkularUser;
import org.hawkular.accounts.api.model.Invitation;
import org.hawkular.accounts.api.model.Organization;
import org.hawkular.accounts.api.model.Role;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Row;

/**
 * @author Juraci Paixão Kröhling
 */
@Stateless
@PermitAll
public class InvitationServiceImpl extends BaseServiceImpl<Invitation> implements InvitationService {
    @Inject
    RoleService roleService;

    @Inject
    OrganizationService organizationService;

    @Inject
    UserService userService;

    @Inject
    OrganizationMembershipService membershipService;

    @Inject @NamedStatement(BoundStatements.INVITATION_GET_BY_TOKEN)
    Instance<BoundStatement> stmtGetByTokenInstance;

    @Inject @NamedStatement(BoundStatements.INVITATIONS_GET_BY_ORGANIZATION)
    Instance<BoundStatement> stmtGetByOrganizationInstance;

    @Inject @NamedStatement(BoundStatements.INVITATIONS_CREATE)
    Instance<BoundStatement> stmtCreateInstance;

    @Inject @NamedStatement(BoundStatements.INVITATIONS_ACCEPT)
    Instance<BoundStatement> stmtAcceptInstance;

    @Inject @NamedStatement(BoundStatements.INVITATIONS_DISPATCH)
    Instance<BoundStatement> stmtDispatchedInstance;

    @Inject @NamedStatement(BoundStatements.INVITATIONS_DELETE)
    Instance<BoundStatement> stmtDeleteInstance;

    @Override
    public Invitation getById(UUID token) {
        return getById(token, stmtGetByTokenInstance.get());
    }

    @Override
    public Invitation get(String id) {
        return getById(UUID.fromString(id));
    }

    @Override
    public Invitation getByToken(String token) {
        return get(token);
    }

    @Override
    public List<Invitation> getPendingInvitationsForOrganization(Organization organization) {
        return getInvitationsForOrganization(organization)
                .stream()
                .filter(i -> i.getAcceptedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<Invitation> getInvitationsForOrganization(Organization organization) {
        if (null == organization) {
            throw new IllegalArgumentException("The given Organization is invalid (null).");
        }
        return getList(stmtGetByOrganizationInstance
                .get()
                .setUUID("organization", organization.getIdAsUUID())
        );
    }

    @Override
    public Invitation create(String email, HawkularUser invitedBy, Organization organization, Role role) {
        BoundStatement stmtCreateStatement = stmtCreateInstance.get();
        Invitation invitation = new Invitation(email, invitedBy, organization, role);
        bindBasicParameters(invitation, stmtCreateStatement);
        stmtCreateStatement.setString("email", invitation.getEmail());
        stmtCreateStatement.setUUID("invitedBy", invitation.getInvitedBy().getIdAsUUID());
        stmtCreateStatement.setUUID("organization", invitation.getOrganization().getIdAsUUID());
        stmtCreateStatement.setUUID("role", invitation.getRole().getIdAsUUID());
        session.execute(stmtCreateStatement);
        return invitation;
    }

    @Override
    public Invitation accept(Invitation invitation, HawkularUser user) {
        BoundStatement stmtAccept = stmtAcceptInstance.get();
        membershipService.create(
                invitation.getOrganization(),
                user,
                invitation.getRole()
        );

        invitation.setAccepted();
        invitation.setAcceptedBy(user);

        stmtAccept.setTimestamp("acceptedAt",
                zonedDateTimeAdapter.convertToDatabaseColumn(invitation.getAcceptedAt())
        );
        update(invitation, stmtAccept);

        return invitation;
    }

    @Override
    public void remove(Invitation invitation) {
        BoundStatement stmtDelete = stmtDeleteInstance.get();
        if (null != invitation) {
            stmtDelete.setUUID("id", invitation.getIdAsUUID());
            session.execute(stmtDelete);
        }
    }

    @Override
    public void markAsDispatched(Invitation invitation) {
        BoundStatement stmtDispatched = stmtDispatchedInstance.get();
        invitation.setDispatched();
        stmtDispatched.setUUID("id", invitation.getIdAsUUID());
        stmtDispatched.setTimestamp("dispatchedAt",
                zonedDateTimeAdapter.convertToDatabaseColumn(invitation.getDispatchedAt())
        );
        update(invitation, stmtDispatched);
    }

    @Override
    Invitation getFromRow(Row row) {
        Role role = roleService.getById(row.getUUID("role"));
        Organization organization = organizationService.getById(row.getUUID("organization"));
        HawkularUser invitedBy = userService.getById(row.getUUID("invitedBy"));
        String email = row.getString("email");

        HawkularUser acceptedBy = null;
        if (!row.isNull("acceptedBy")) {
            acceptedBy = userService.getById(row.getUUID("acceptedBy"));
        }

        ZonedDateTime acceptedAt = null;
        if (!row.isNull("acceptedAt")) {
            acceptedAt = zonedDateTimeAdapter.convertToEntityAttribute(row.getTimestamp("acceptedAt"));
        }

        ZonedDateTime dispatchedAt = null;
        if (!row.isNull("dispatchedAt")) {
            dispatchedAt = zonedDateTimeAdapter.convertToEntityAttribute(row.getTimestamp("dispatchedAt"));
        }

        Invitation.Builder builder = new Invitation.Builder();
        mapBaseFields(row, builder);

        return builder
                .role(role)
                .acceptedAt(acceptedAt)
                .dispatchedAt(dispatchedAt)
                .organization(organization)
                .acceptedBy(acceptedBy)
                .invitedBy(invitedBy)
                .email(email)
                .build();
    }
}
