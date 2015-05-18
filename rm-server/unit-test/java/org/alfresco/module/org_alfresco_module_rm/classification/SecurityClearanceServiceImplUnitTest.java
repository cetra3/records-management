/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.module.org_alfresco_module_rm.classification;

import static org.alfresco.module.org_alfresco_module_rm.classification.model.ClassifiedContentModel.ASPECT_SECURITY_CLEARANCE;
import static org.alfresco.module.org_alfresco_module_rm.classification.model.ClassifiedContentModel.PROP_CLEARANCE_LEVEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.alfresco.module.org_alfresco_module_rm.test.util.AlfMock.*;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.alfresco.module.org_alfresco_module_rm.classification.ClassificationServiceException.LevelIdNotFound;
import org.alfresco.module.org_alfresco_module_rm.test.util.MockAuthenticationUtilHelper;
import org.alfresco.module.org_alfresco_module_rm.util.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.security.PersonService.PersonInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link SecurityClearanceServiceImpl}.
 *
 * @author Neil Mc Erlean
 * @author David Webster
 * @since 3.0
 */
public class SecurityClearanceServiceImplUnitTest
{
    @InjectMocks private SecurityClearanceServiceImpl securityClearanceServiceImpl;

    @Mock private AuthenticationUtil         mockAuthenticationUtil;
    @Mock private ClassificationLevelManager mockClassificationLevelManager;
    @Mock private DictionaryService          mockDictionaryService;
    @Mock private NodeService                mockNodeService;
    @Mock private PersonService              mockPersonService;
    @Mock private ClassificationService      mockClassificationService;
    @Mock private ClearanceLevelManager      mockClearanceLevelManager;

    @Before public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    private PersonInfo createMockPerson(String userName, String firstName, String lastName, String clearanceLevel)
    {
        final NodeRef    userNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, userName);
        final PersonInfo info     = new PersonInfo(userNode, userName, firstName, lastName);

        when(mockPersonService.getPerson(eq(userName), anyBoolean())).thenReturn(userNode);
        when(mockPersonService.getPerson(userNode)).thenReturn(info);

        when(mockNodeService.hasAspect(userNode, ASPECT_SECURITY_CLEARANCE)).thenReturn(clearanceLevel != null);
        when(mockNodeService.getProperty(userNode, PROP_CLEARANCE_LEVEL)).thenReturn(clearanceLevel);

        if (clearanceLevel != null)
        {
            final ClassificationLevel dummyValue = new ClassificationLevel(clearanceLevel, clearanceLevel);
            when(mockClassificationLevelManager.findLevelById(clearanceLevel)).thenReturn(dummyValue);
        }

        return info;
    }

    @Test public void userWithNoClearanceGetsDefaultClearance()
    {
        final PersonInfo user1 = createMockPerson("user1", "User", "One", null);
        MockAuthenticationUtilHelper.setup(mockAuthenticationUtil, user1.getUserName());
        when(mockClassificationService.getUnclassifiedClassificationLevel())
            .thenReturn(ClassificationLevelManager.UNCLASSIFIED);
        when(mockClearanceLevelManager.findLevelByClassificationLevelId(ClassificationLevelManager.UNCLASSIFIED_ID))
            .thenReturn(ClearanceLevelManager.NO_CLEARANCE);

        final SecurityClearance clearance = securityClearanceServiceImpl.getUserSecurityClearance();

        assertEquals(ClassificationLevelManager.UNCLASSIFIED, clearance.getClearanceLevel().getHighestClassificationLevel());

    }

    /** Check that a user can have their clearance set by an authorised user. */
    @Test public void setUserSecurityClearance_setClearance()
    {
        // Create the clearance.
        String topSecretId = "ClearanceId";
        ClassificationLevel level = new ClassificationLevel(topSecretId, "TopSecretKey");
        ClearanceLevel clearanceLevel = new ClearanceLevel(level, "TopSecretKey");
        when(mockClearanceLevelManager.findLevelByClassificationLevelId(topSecretId)).thenReturn(clearanceLevel);
        when(mockClassificationLevelManager.getClassificationLevels()).thenReturn(ImmutableList.of(level));

        // Create the authorised user.
        String authorisedUserName = "authorisedUser";
        when(mockAuthenticationUtil.getFullyAuthenticatedUser()).thenReturn(authorisedUserName);
        NodeRef authorisedPersonNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, authorisedUserName);
        PersonInfo authorisedPersonInfo = new PersonInfo(authorisedPersonNode, authorisedUserName, "first", "last");
        when(mockPersonService.getPerson(authorisedUserName, false)).thenReturn(authorisedPersonNode);
        when(mockPersonService.getPerson(authorisedPersonNode)).thenReturn(authorisedPersonInfo);

        // The authorised user is cleared to use this clearance.
        when(mockNodeService.hasAspect(authorisedPersonNode, ASPECT_SECURITY_CLEARANCE)).thenReturn(true);
        when(mockNodeService.getProperty(authorisedPersonNode, PROP_CLEARANCE_LEVEL)).thenReturn(topSecretId);

        // Create the user who will have their clearance set.
        String userName = "User 1";
        NodeRef personNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, userName);
        PersonInfo personInfo = new PersonInfo(personNode, userName, "first", "last");
        when(mockPersonService.getPerson(userName, false)).thenReturn(personNode);
        when(mockPersonService.getPerson(personNode)).thenReturn(personInfo);

        // Once the user's clearance has been set then the node service is queried about it.
        when(mockNodeService.hasAspect(personNode, ASPECT_SECURITY_CLEARANCE)).thenReturn(true);
        when(mockNodeService.getProperty(personNode, PROP_CLEARANCE_LEVEL)).thenReturn(topSecretId);

        // Call the method under test.
        SecurityClearance securityClearance = securityClearanceServiceImpl.setUserSecurityClearance(userName, topSecretId);

        // Check the returned value.
        assertEquals(personInfo, securityClearance.getPersonInfo());
        assertEquals(clearanceLevel, securityClearance.getClearanceLevel());
        // Check the value stored in the node service.
        verify(mockNodeService).setProperty(personNode, PROP_CLEARANCE_LEVEL, topSecretId);
    }

    /** Check that a user cannot raise someone else's clearance above their own. */
    @Test(expected = LevelIdNotFound.class)
    public void setUserSecurityClearance_insufficientClearance()
    {
        // Create the "Top Secret" and "Confidential" clearances.
        String topSecretId = "TopSecretClearanceId";
        ClassificationLevel topSecret = new ClassificationLevel(topSecretId, "TopSecretKey");
        ClearanceLevel topSecretClearance = new ClearanceLevel(topSecret, "TopSecretKey");
        when(mockClearanceLevelManager.findLevelByClassificationLevelId(topSecretId)).thenReturn(topSecretClearance);
        String confidentialId = "ConfidentialClearanceId";
        ClassificationLevel confidential = new ClassificationLevel(confidentialId, "ConfidentialKey");
        ClearanceLevel confidentialClearance = new ClearanceLevel(confidential, "ConfidentialKey");
        when(mockClearanceLevelManager.findLevelByClassificationLevelId(confidentialId)).thenReturn(confidentialClearance);
        when(mockClassificationLevelManager.getClassificationLevels()).thenReturn(ImmutableList.of(topSecret, confidential));

        // Create the user attempting to use the API with "Confidential" clearance.
        String userName = "unauthorisedUser";
        when(mockAuthenticationUtil.getFullyAuthenticatedUser()).thenReturn(userName);
        NodeRef personNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, userName);
        PersonInfo personInfo = new PersonInfo(personNode, userName, "first", "last");
        when(mockPersonService.getPerson(userName, false)).thenReturn(personNode);
        when(mockPersonService.getPerson(personNode)).thenReturn(personInfo);

        // The authorised user is cleared to use this clearance.
        when(mockNodeService.hasAspect(personNode, ASPECT_SECURITY_CLEARANCE)).thenReturn(true);
        when(mockNodeService.getProperty(personNode, PROP_CLEARANCE_LEVEL)).thenReturn(confidentialId);

        // Create the user who will have their clearance set.
        String targetUserName = "Target User";
        NodeRef targetPersonNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, targetUserName);
        PersonInfo targetPersonInfo = new PersonInfo(targetPersonNode, targetUserName, "first", "last");
        when(mockPersonService.getPerson(targetUserName, false)).thenReturn(targetPersonNode);
        when(mockPersonService.getPerson(targetPersonNode)).thenReturn(targetPersonInfo);

        // Call the method under test and expect an exception.
        securityClearanceServiceImpl.setUserSecurityClearance(targetUserName, topSecretId);
    }

    /**
     * Check that a user with no clearance is not cleared to use the "Secret" classification.
     */
    @Test public void isClearedForClassification()
    {
        ClassificationLevel topSecret = new ClassificationLevel("1", "TopSecret");
        ClassificationLevel secret = new ClassificationLevel("2", "Secret");
        ImmutableList<ClassificationLevel> classificationLevels = ImmutableList.of(topSecret, secret);
        when(mockClassificationLevelManager.getClassificationLevels()).thenReturn(classificationLevels);

        SecurityClearance clearance = new SecurityClearance(mock(PersonInfo.class), ClearanceLevelManager.NO_CLEARANCE);

        // Call the method under test.
        boolean result = securityClearanceServiceImpl.isClearedForClassification(clearance, "2");

        assertFalse("A user with no clearance should not be able to access the 'Secret' classification.", result);
    }

    /**
     * Check that all levels are returned
     */
    @Test public void getClearanceLevels()
    {

        // Create a list of clearance levels
        ImmutableList<ClearanceLevel> mockClearanceLevels = ImmutableList.of(
            new ClearanceLevel(new ClassificationLevel("level1", "Level One"), "Clearance One"),
            new ClearanceLevel(new ClassificationLevel("level2", "Level Two"), "Clearance Two"),
            new ClearanceLevel(new ClassificationLevel("level3", "Level Three"), "Clearance Three")
        );

        when(mockClearanceLevelManager.getClearanceLevels())
            .thenReturn(mockClearanceLevels);
        when(mockClearanceLevelManager.getMostSecureLevel())
            .thenReturn(mockClearanceLevels.get(0));

        List<ClearanceLevel> actualClearanceLevels = securityClearanceServiceImpl.getClearanceLevels();

        assertEquals(mockClearanceLevels.size(), actualClearanceLevels.size());
        assertEquals(mockClearanceLevels.get(0), actualClearanceLevels.get(0));
        assertEquals(mockClearanceLevels.get(1), actualClearanceLevels.get(1));
        assertEquals(mockClearanceLevels.get(2), actualClearanceLevels.get(2));
    }

    /**
     * Check that a user with restricted access only gets some of the levels.
     */
    @Test
    public void getRestrictedClearanceLevels()
    {

        // Create a list of clearance levels
        ImmutableList<ClearanceLevel> mockClearanceLevels = ImmutableList.of(
            new ClearanceLevel(new ClassificationLevel("level1", "Level One"), "Clearance One"),
            new ClearanceLevel(new ClassificationLevel("level2", "Level Two"), "Clearance Two"),
            new ClearanceLevel(new ClassificationLevel("level3", "Level Three"), "Clearance Three")
        );

        when(mockClearanceLevelManager.getClearanceLevels()).thenReturn(mockClearanceLevels);
        when(mockClearanceLevelManager.getMostSecureLevel()).thenReturn(mockClearanceLevels.get(1));

        List<ClearanceLevel> restrictedClearanceLevels = securityClearanceServiceImpl.getClearanceLevels();

        assertEquals(2, restrictedClearanceLevels.size());
        assertEquals(mockClearanceLevels.get(1), restrictedClearanceLevels.get(0));
        assertEquals(mockClearanceLevels.get(2), restrictedClearanceLevels.get(1));
    }
}
