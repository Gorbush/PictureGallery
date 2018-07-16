/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gallerymine.backend.services;

import com.querydsl.core.types.dsl.BooleanExpression;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.data.RetryRunner;
import gallerymine.backend.data.RetryVersion;
import gallerymine.model.Process;
import gallerymine.model.QProcess;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessDetails;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class ProcessService {

    private static Logger log = LoggerFactory.getLogger(ImportRequestService.class);

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ImportRequestRepository importRepository;

    public List<Process> getTop(ProcessStatus status, ProcessType type) {
        QProcess processExample = new QProcess("proc");
        BooleanExpression predicate = processExample.finished.isNull();
        if (status != null) {
            predicate = predicate.and(processExample.status.eq(status));
        }
        if (type != null) {
            predicate = predicate.and(processExample.type.eq(type));
        }
        Iterable<Process> processes = processRepository.findAll(predicate,
                new Sort(new Sort.Order(Sort.Direction.DESC, "started")));

        List<Process> processList = new ArrayList<>();
        processes.forEach(processList::add);
        return processList;
    }

    public List<ProcessDetails> populateDetails(List<Process> processes) {
        List<ProcessDetails> running = new ArrayList<>();
        for (Process process: processes) {
            ProcessDetails details = populateDetails(process);
            running.add(details);
        }


        return running;
    }

    public ProcessDetails populateDetails(Process process) {
        ProcessDetails details = new ProcessDetails();
        details.setProcess(process);
        if (process != null) {
            switch (process.getType()) {
                case IMPORT: {
                    details.setDetail(importRepository.findByIndexProcessIdsContains(process.getId()));
                    break;
                }
                case MATCHING: {
                    details.setDetail(importRepository.findByIndexProcessIdsContains(process.getId()));
                    break;
                }
                case APPROVAL: {
                    details.setDetail(importRepository.findByIndexProcessIdsContains(process.getId()));
                    break;
                }
                default: {
                    details.setDetail(importRepository.findByIndexProcessIdsContains(process.getId()));
                    break;
                }
            }
        }
        return details;
    }

    public ProcessDetails populateAllDetails(Process process) {
        ProcessDetails details = new ProcessDetails();
        details.setProcess(process);

        switch (process.getType()) {
            case IMPORT: {
                details.setDetails(importRepository.findAllByIndexProcessIdsIsContainingAndParentIsNullOrderByCreatedAsc(process.getId()));
                break;
            }
            case MATCHING: {
                details.setDetails(importRepository.findAllByIndexProcessIdsIsContainingAndParentIsNullOrderByCreatedAsc(process.getId()));
                break;
            }
            case APPROVAL: {
                details.setDetails(importRepository.findAllByIndexProcessIdsIsContainingAndParentIsNullOrderByCreatedAsc(process.getId()));
                break;
            }
            default: {
                details.setDetails(importRepository.findAllByIndexProcessIdsIsContainingAndParentIsNullOrderByCreatedAsc(process.getId()));
                break;
            }

        }
        return details;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process retrySave(String entityId, RetryRunner<Process> runner) {
        Process request = processRepository.findOne(entityId);
        request = runner.run(request);
        if (request != null) {
            processRepository.save(request);
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process updateStatus(String requestId, ProcessStatus newStatus) {
        Process request = processRepository.findOne(requestId);
        ProcessStatus status = request.getStatus();
        if (!status.equals(newStatus)) {
            request.setStatus(newStatus);
            processRepository.save(request);
            log.info(" process status changed new={} old={} name={}", newStatus, status, request.getName());
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process addError(String requestId, String error, Object... params) {
        Process request = processRepository.findOne(requestId);
        request.addError(error, params);
        processRepository.save(request);
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process addError(String requestId, ProcessStatus status, String error, Object... params) {
        Process request = processRepository.findOne(requestId);
        request.setStatus(status);
        request.addError(error, params);
        processRepository.save(request);
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process addNotes(String requestId, ProcessStatus status, Collection<String> notes) {
        Process request = processRepository.findOne(requestId);
        request.setStatus(status);
        request.getNotes().addAll(notes);
        processRepository.save(request);
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public Process addNote(String requestId, String note, Object... params) {
        Process request = processRepository.findOne(requestId);
        request.addNote(note, params);
        processRepository.save(request);
        return request;
    }
}
