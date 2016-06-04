/*
 * Copyright (C) 2001-2016 Food and Agriculture Organization of the
 * United Nations (FAO-UN), United Nations World Food Programme (WFP)
 * and United Nations Environment Programme (UNEP)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 *
 * Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
 * Rome - Italy. email: geonetwork@osgeo.org
 */

package org.fao.geonet.api.groups;

import org.fao.geonet.ApplicationContextHolder;
import org.fao.geonet.api.API;
import org.fao.geonet.domain.Group;
import org.fao.geonet.domain.Group_;
import org.fao.geonet.domain.Profile;
import org.fao.geonet.domain.ReservedGroup;
import org.fao.geonet.domain.UserGroup;
import org.fao.geonet.repository.GroupRepository;
import org.fao.geonet.repository.SortUtils;
import org.fao.geonet.repository.UserGroupRepository;
import org.fao.geonet.repository.specification.GroupSpecs;
import org.fao.geonet.repository.specification.UserGroupSpecs;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jeeves.server.UserSession;
import jeeves.server.context.ServiceContext;

@RequestMapping(value = {
    "/api/groups",
    "/api/" + API.VERSION_0_1 +
        "/groups"
})
@Api(value = "groups",
    tags = "groups",
    description = "Groups operations")
@Controller("groups")
public class GroupsApi {

    @ApiOperation(
        value = "Get groups",
        notes = "Return all catalog groups when not authenticated or " +
            "administrator with or without reserved groups. " +
            "When authenticated, return user groups " +
            "optionnaly filtered on a specific" +
            "user profile.",
        nickname = "getGroups")
    @RequestMapping(
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public List<Group> getGroups(
        @ApiParam(
            value = "Including Internet, Intranet, Guest groups"
        )
        @RequestParam(
            required = false,
            defaultValue = "false"
        )
        boolean withReservedGroup,
        @ApiParam(
            value = "For a specific profile"
        )
        @RequestParam(
            required = false
        )
        String profile
    ) throws Exception {
        ServiceContext context = ServiceContext.get();
        UserSession session = context.getUserSession();

        if (!session.isAuthenticated() || profile == null) {
            return getGroups(
                context,
                null,
                withReservedGroup,
                !withReservedGroup);
        } else {
            return getGroups(
                context,
                Profile.findProfileIgnoreCase(profile),
                false, false);
        }
    }

    /**
     * Retrieves a user's groups.
     *
     * @param includingSystemGroups if true, also returns the system groups ('GUEST', 'intranet',
     *                              'all')
     * @param all                   if true returns all the groups, even those the user doesn't
     *                              belongs to
     */
    private List<Group> getGroups(
        ServiceContext context,
        Profile profile,
        boolean includingSystemGroups,
        boolean all)
        throws SQLException {

        final GroupRepository groupRepository = context.getBean(GroupRepository.class);
        final UserGroupRepository userGroupRepository = context.getBean(UserGroupRepository.class);
        final Sort sort = SortUtils.createSort(Group_.id);

        UserSession session = context.getUserSession();
        if (all || !session.isAuthenticated() || Profile.Administrator == session.getProfile()) {
            if (includingSystemGroups) {
                return groupRepository.findAll(null, sort);
            } else {
                return groupRepository.findAll(Specifications.not(GroupSpecs.isReserved()), sort);
            }
        } else {
            Specifications<UserGroup> spec = Specifications.where(UserGroupSpecs.hasUserId(session.getUserIdAsInt()));
            // you're no Administrator
            // retrieve your groups
            if (profile != null) {
                spec = spec.and(UserGroupSpecs.hasProfile(profile));
            }
            Set<Integer> ids = new HashSet<Integer>(userGroupRepository.findGroupIds(spec));

            // include system groups if requested (used in harvesters)
            if (includingSystemGroups) {
                // these DB keys of system groups are hardcoded !
                for (ReservedGroup reservedGroup : ReservedGroup.values()) {
                    ids.add(reservedGroup.getId());
                }
            }

            // retrieve all groups and filter to only user one
            List<Group> groups = groupRepository
                .findAll(null, sort);
            groups.removeIf(g -> !ids.contains(g.getId()));
            return groups;
        }
    }
}
