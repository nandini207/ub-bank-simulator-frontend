package com.billdesk.simulator.controller;

import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.repository.TransactionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for the Tester Control Panel.
 *
 * URL: GET  /control - shows the control panel page
 *      POST /control - saves the tester's settings
 *
 * This is ONLY for testers. BillDesk's connector never calls this endpoint.
 */
@Controller
@RequestMapping("/control")
public class ControlPanelController {

    // Shared settings - same object used by PaymentService
    private final SimulatorSettings simulatorSettings;

    // Repository - to show transaction log on control panel
    private final TransactionRepository transactionRepository;

    public ControlPanelController(SimulatorSettings simulatorSettings,
                                   TransactionRepository transactionRepository) {
        this.simulatorSettings = simulatorSettings;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Shows the control panel page with current settings and transaction log.
     */
    @GetMapping
    public String showControlPanel(Model model) {
        // Pass current settings to the HTML page
        model.addAttribute("settings", simulatorSettings);

        // Pass all SimulatorOutcome values for the dropdown
        model.addAttribute("allOutcomes", SimulatorOutcome.values());

        // Pass all transactions for the session log
        model.addAttribute("transactions", transactionRepository.findAll());
        model.addAttribute("transactionCount", transactionRepository.count());

        return "control";
    }

    /**
     * Saves the settings submitted from the control panel form.
     * Then redirects back to GET /control to show updated settings.
     */
    @PostMapping
    public String saveSettings(
            @RequestParam(value = "defaultOutcome",          defaultValue = "SUCCESS") String outcome,
            @RequestParam(value = "callbackDelaySeconds",    defaultValue = "0")       int delaySeconds,
            @RequestParam(value = "dropCallback",            defaultValue = "false")   boolean dropCallback,
            @RequestParam(value = "duplicateCallback",       defaultValue = "false")   boolean duplicateCallback,
            @RequestParam(value = "pendingCheckerDelay",     defaultValue = "10")      int pendingDelay,
            @RequestParam(value = "pendingFinalOutcome",     defaultValue = "SUCCESS") String pendingOutcome) {

        // Save all settings to the shared settings object
        simulatorSettings.setDefaultOutcome(SimulatorOutcome.valueOf(outcome));
        simulatorSettings.setCallbackDelaySeconds(delaySeconds);
        simulatorSettings.setDropCallback(dropCallback);
        simulatorSettings.setDuplicateCallback(duplicateCallback);
        simulatorSettings.setPendingCheckerDelaySeconds(pendingDelay);
        simulatorSettings.setPendingFinalOutcome(SimulatorOutcome.valueOf(pendingOutcome));

        // Redirect back to GET /control to show the updated page
        return "redirect:/control";
    }
}
