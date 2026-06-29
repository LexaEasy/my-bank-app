package ru.practicum.bank.blocker.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.bank.blocker.service.BlockerService;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;

@RestController
@RequestMapping("/api/blocker")
public class BlockerController {

    private final BlockerService blockerService;

    public BlockerController(BlockerService blockerService) {
        this.blockerService = blockerService;
    }

    @PostMapping("/check")
    public OperationCheckResponse check(@Valid @RequestBody OperationCheckRequest request) {
        return blockerService.check(request);
    }
}
