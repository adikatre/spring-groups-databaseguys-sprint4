package com.open.spring.mvc.groups;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.open.spring.mvc.person.Person;
import com.open.spring.mvc.person.PersonDetailsService;
import com.open.spring.mvc.person.PersonJpaRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@RestController
@RequestMapping("/api/groups")
public class GroupsApiController {

    @Autowired
    private GroupsJpaRepository groupsRepository;

    @Autowired
    private PersonJpaRepository personRepository;

    @Autowired
    private PersonDetailsService personDetailsService;

    // ===== DTOs =====
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupCreateDto {
        private String name;
        private String period;
        private String course;        
        private List<Long> memberIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupUpdateDto {
        private String name;
        private String period;
        private String course;        
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkGroupCreateDto {
        private List<GroupCreateDto> groups;
    }

    // ===== Helper Methods =====
    private Map<String, Object> buildGroupResponse(Groups group) {
        Map<String, Object> groupMap = new LinkedHashMap<>();
        groupMap.put("id", group.getId());
        groupMap.put("name", group.getName());
        groupMap.put("period", group.getPeriod());
        groupMap.put("course", group.getCourse());

        List<Map<String, Object>> membersList = new ArrayList<>();
        List<Object[]> memberRows = groupsRepository.findGroupMembersRaw(group.getId());

        for (Object[] row : memberRows) {
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("id", ((Number) row[0]).longValue());
            member.put("uid", (String) row[1]);
            member.put("name", (String) row[2]);
            member.put("email", (String) row[3]);
            membersList.add(member);
        }

        groupMap.put("members", membersList);
        return groupMap;
    }

    // ===== GET Operations =====

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAllGroups() {
        try {
            List<Groups> groups = groupsRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Groups group : groups) {
                result.add(buildGroupResponse(group));
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getGroupById(@PathVariable Long id) {
        try {
            Optional<Groups> groupOpt = groupsRepository.findById(id);
            if (groupOpt.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(buildGroupResponse(groupOpt.get()), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/person/{personId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getGroupsByPersonId(@PathVariable Long personId) {
        try {
            Optional<Person> personOpt = personRepository.findById(personId);
            if (personOpt.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            List<Groups> groups = groupsRepository.findGroupsByPersonId(personId);
            List<Map<String, Object>> result = new ArrayList<>();

            for (Groups group : groups) {
                result.add(buildGroupResponse(group));
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> searchGroupsByName(
            @org.springframework.web.bind.annotation.RequestParam(value = "name", required = true) String searchTerm) {
        try {
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return new ResponseEntity<>(
                    new ArrayList<>(),
                    HttpStatus.OK
                );
            }

            List<Groups> groups = groupsRepository.searchByName(searchTerm.trim());
            List<Map<String, Object>> result = new ArrayList<>();

            for (Groups group : groups) {
                result.add(buildGroupResponse(group));
            }

            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ===== POST Operations =====

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody GroupCreateDto dto) {
        try {
            if (dto.getName() == null || dto.getName().isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Group name is required"),
                    HttpStatus.BAD_REQUEST
                );
            }

            Groups group = new Groups();
            group.setName(dto.getName());
            group.setPeriod(dto.getPeriod());
            group.setCourse(dto.getCourse());

            Groups savedGroup = groupsRepository.save(group);

            if (dto.getMemberIds() != null) {
                for (Long personId : dto.getMemberIds()) {
                    groupsRepository.addPersonToGroupDirect(savedGroup.getId(), personId);
                }
                savedGroup = groupsRepository.findById(savedGroup.getId()).orElse(savedGroup);
            }

            return new ResponseEntity<>(buildGroupResponse(savedGroup), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @PostMapping("/bulk")
    @Transactional
    public ResponseEntity<Map<String, Object>> bulkCreateGroups(@RequestBody BulkGroupCreateDto dto) {
        try {
            List<Map<String, Object>> created = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            if (dto.getGroups() == null || dto.getGroups().isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "No groups provided"),
                    HttpStatus.BAD_REQUEST
                );
            }

            for (GroupCreateDto groupDto : dto.getGroups()) {
                try {
                    Groups group = new Groups();
                    group.setName(groupDto.getName());
                    group.setPeriod(groupDto.getPeriod());
                    group.setCourse(groupDto.getCourse());

                    Groups savedGroup = groupsRepository.save(group);

                    if (groupDto.getMemberIds() != null) {
                        for (Long personId : groupDto.getMemberIds()) {
                            groupsRepository.addPersonToGroupDirect(savedGroup.getId(), personId);
                        }
                        savedGroup = groupsRepository.findById(savedGroup.getId()).orElse(savedGroup);
                    }

                    created.add(buildGroupResponse(savedGroup));
                } catch (Exception e) {
                    errors.add("Failed to create group '" + groupDto.getName() + "': " + e.getMessage());
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("created", created);
            if (!errors.isEmpty()) {
                response.put("errors", errors);
            }

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    // ===== PUT Operations =====

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateGroup(
            @PathVariable Long id,
            @RequestBody GroupUpdateDto dto) {
        try {
            Optional<Groups> groupOpt = groupsRepository.findById(id);
            if (groupOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Group not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            Groups group = groupOpt.get();

            if (dto.getName() != null && !dto.getName().isEmpty()) {
                group.setName(dto.getName());
            }
            if (dto.getPeriod() != null) {
                group.setPeriod(dto.getPeriod());
            }
            if (dto.getCourse() != null) {
                group.setCourse(dto.getCourse());
            }

            Groups updatedGroup = groupsRepository.save(group);
            return new ResponseEntity<>(buildGroupResponse(updatedGroup), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    // ===== DELETE Operations =====

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup (@PathVariable Long id) {
        try {
            Optional<Groups> groupOpt = groupsRepository.findById(id);
            if (groupOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Group not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            groupsRepository.deleteById(id);
            return new ResponseEntity<>(
                Map.of("message", "Group deleted successfully"),
                HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @DeleteMapping("/{id}/members/{personId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removePersonFromGroup(
            @PathVariable Long id,
            @PathVariable Long personId) {
        try {
            Optional<Groups> groupOpt = groupsRepository.findById(id);
            if (groupOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Group not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            Optional<Person> personOpt = personRepository.findById(personId);
            if (personOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Person not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            Groups group = groupOpt.get();
            Person person = personOpt.get();

            if (!group.getGroupMembers().contains(person)) {
                return new ResponseEntity<>(
                    Map.of("error", "Person not in group"),
                    HttpStatus.CONFLICT
                );
            }

            group.removePerson(person);
            groupsRepository.save(group);

            return new ResponseEntity<>(buildGroupResponse(group), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @PostMapping("/{id}/members/{personId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> addPersonToGroup(
            @PathVariable Long id,
            @PathVariable Long personId) {
        try {
            Optional<Groups> groupOpt = groupsRepository.findById(id);
            if (groupOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Group not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            Optional<Person> personOpt = personRepository.findById(personId);
            if (personOpt.isEmpty()) {
                return new ResponseEntity<>(
                    Map.of("error", "Person not found"),
                    HttpStatus.NOT_FOUND
                );
            }

            Groups group = groupOpt.get();
            Person person = personOpt.get();

            if (group.getGroupMembers().contains(person)) {
                return new ResponseEntity<>(
                    Map.of("error", "Person already in group"),
                    HttpStatus.CONFLICT
                );
            }

            group.addPerson(person);
            groupsRepository.save(group);

            return new ResponseEntity<>(buildGroupResponse(group), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                Map.of("error", e.getMessage()),
                HttpStatus.BAD_REQUEST
            );
        }
    }
}