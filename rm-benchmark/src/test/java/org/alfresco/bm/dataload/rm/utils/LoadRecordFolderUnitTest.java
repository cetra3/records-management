/*
 * Copyright (C) 2005-2017 Alfresco Software Limited.
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

package org.alfresco.bm.dataload.rm.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.alfresco.bm.cm.FolderData;
import org.alfresco.bm.dataload.LoadSingleComponentUnitTest;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.rest.rm.community.model.recordcategory.RecordCategory;
import org.alfresco.rest.rm.community.model.recordcategory.RecordCategoryChild;
import org.alfresco.utility.model.UserModel;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Test;
import org.mockito.Mockito;

import com.mongodb.DBObject;

/**
* Unit tests for load record folder operation from LoadSingleComponent
*
* @author Silviu Dinuta
* @since 2.6
*/
public class LoadRecordFolderUnitTest extends LoadSingleComponentUnitTest
{
    private static final String EVENT_RECORD_FOLDER_LOADED = "testRecordFolderLoaded";

    @Test
    public void testLoadRecordFolderOperation() throws Exception
    {
        loadSingleComponent.setEventNameComplete(EVENT_RECORD_FOLDER_LOADED);

        Event mockedEvent = mock(Event.class);
        DBObject mockedData = mock(DBObject.class);
        when(mockedData.get(FIELD_CONTEXT)).thenReturn(FILEPLAN_CONTEXT);
        when(mockedData.get(FIELD_PATH)).thenReturn("/aPath");
        when(mockedData.get(FIELD_LOAD_OPERATION)).thenReturn(LOAD_RECORD_FOLDER_OPERATION);

        when(mockedEvent.getData()).thenReturn(mockedData);
        FolderData mockedFolder = mock(FolderData.class);
        when(mockedFolder.getId()).thenReturn("folderId");
        when(mockedFolder.getPath()).thenReturn("/aPath");
        when(mockedFileFolderService.getFolder(FILEPLAN_CONTEXT, "/aPath")).thenReturn(mockedFolder);
        loadSingleComponent.setFileFolderService(mockedFileFolderService);
        when(mockedEvent.getSessionId()).thenReturn("someId");

        when(mockedRestApiFactory.getFilePlansAPI(any(UserModel.class))).thenReturn(mockedFilePlanAPI);
        when(mockedRestApiFactory.getRecordCategoryAPI(any(UserModel.class))).thenReturn(mockedRecordCategoryAPI);

        RecordCategory mockedFilePlanComponent = mock(RecordCategory.class);
        when(mockedFilePlanComponent.getId()).thenReturn("folderId");
        when(mockedRecordCategoryAPI.getRecordCategory("folderId")).thenReturn(mockedFilePlanComponent);

        RecordCategoryChild mockedChildFilePlanComponent = mock(RecordCategoryChild.class);
        when(mockedChildFilePlanComponent.getId()).thenReturn(UUID.randomUUID().toString());
        when(mockedRecordCategoryAPI.createRecordCategoryChild(any(RecordCategoryChild.class), eq("folderId"))).thenReturn(mockedChildFilePlanComponent);

        mockSiteAndUserData();

        EventResult result = loadSingleComponent.processEvent(mockedEvent, new StopWatch());
        verify(mockedFileFolderService, times(1)).createNewFolder(any(String.class), any(String.class), any(String.class));
        verify(mockedFileFolderService, times(1)).incrementFolderCount(any(String.class), any(String.class), any(Long.class));
        assertEquals(true, result.isSuccess());
        DBObject data = (DBObject) result.getData();
        assertEquals("Created 1 record folder.", data.get("msg"));
        assertEquals("/aPath", data.get(FIELD_PATH));
        assertEquals("aUser", data.get("username"));
        assertEquals(1, result.getNextEvents().size());

        Event event = result.getNextEvents().get(0);
        assertEquals(EVENT_RECORD_FOLDER_LOADED, event.getName());
    }

    @Test
    public void testLoadRecordFolderOperationWithExceptionOnRestApi() throws Exception
    {

        loadSingleComponent.setEventNameComplete(EVENT_RECORD_FOLDER_LOADED);

        Event mockedEvent = mock(Event.class);
        DBObject mockedData = mock(DBObject.class);
        when(mockedData.get(FIELD_CONTEXT)).thenReturn(FILEPLAN_CONTEXT);
        when(mockedData.get(FIELD_PATH)).thenReturn("/aPath");
        when(mockedData.get(FIELD_LOAD_OPERATION)).thenReturn(LOAD_RECORD_FOLDER_OPERATION);
        when(mockedEvent.getData()).thenReturn(mockedData);

        FolderData mockedFolder = mock(FolderData.class);
        when(mockedFolder.getId()).thenReturn("folderId");
        when(mockedFolder.getPath()).thenReturn("/aPath");
        when(mockedFileFolderService.getFolder(FILEPLAN_CONTEXT, "/aPath")).thenReturn(mockedFolder);
        loadSingleComponent.setFileFolderService(mockedFileFolderService);
        when(mockedEvent.getSessionId()).thenReturn("someId");

        when(mockedRestApiFactory.getRecordCategoryAPI(any(UserModel.class))).thenReturn(mockedRecordCategoryAPI);

        Mockito.doThrow(new RuntimeException("someError")).when(mockedRecordCategoryAPI).createRecordCategoryChild(any
                (RecordCategoryChild.class), any(String.class));
        mockSiteAndUserData();

        EventResult result = loadSingleComponent.processEvent(mockedEvent, new StopWatch());
        verify(mockedFileFolderService, never()).createNewFolder(any(String.class), any(String.class), any(String.class));
        verify(mockedFileFolderService, never()).incrementFolderCount(any(String.class), any(String.class), any(Long.class));
        assertEquals(false, result.isSuccess());
        DBObject data = (DBObject) result.getData();
        assertNotNull(data.get("error"));
        assertEquals("aUser", data.get("username"));
        assertEquals(mockedFolder.getPath(), data.get("path"));
        assertNotNull(data.get("stack"));
        assertEquals(0, result.getNextEvents().size());
    }
}
