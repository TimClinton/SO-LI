package com.example.memapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/")
    public String postIndex(@RequestParam("MV_kb") String mvStr,
                            @RequestParam("MO_kb") String moStr,
                            @RequestParam("page_kb") String pageStr,
                            Model model) {
        int MV_kb, MO_kb, page_kb;
        try {
            MV_kb = Integer.parseInt(mvStr);
            MO_kb = Integer.parseInt(moStr);
            page_kb = Integer.parseInt(pageStr);
            logger.info("Received MV_kb: {}, MO_kb: {}, page_kb: {}", MV_kb, MO_kb, page_kb);
        } catch (NumberFormatException e) {
            logger.error("Invalid input values: {}", e.getMessage());
            model.addAttribute("error", "Valorile introduse trebuie să fie numere întregi.");
            return "index";
        }

        int MV_bytes = MV_kb * 1024;
        int MO_bytes = MO_kb * 1024;
        int page_size_bytes = page_kb * 1024;

        int num_pages_virtual = MV_bytes / page_size_bytes;
        int num_pages_physical = MO_bytes / page_size_bytes;

        logger.info("Computed MV_bytes: {}, MO_bytes: {}, page_size_bytes: {}", MV_bytes, MO_bytes, page_size_bytes);
        logger.info("Computed num_pages_virtual: {}, num_pages_physical: {}", num_pages_virtual, num_pages_physical);

        if (num_pages_virtual == 0) {
            logger.warn("Memoria virtuală este prea mică pentru dimensiunea unei pagini.");
            model.addAttribute("error", "Memoria virtuală este prea mică pentru dimensiunea unei pagini.");
            return "index";
        }

        if (num_pages_physical == 0) {
            logger.warn("Memoria fizică este prea mică pentru dimensiunea unei pagini.");
            model.addAttribute("error", "Memoria fizică este prea mică pentru dimensiunea unei pagini.");
            return "index";
        }

        // Verificăm dacă num_pages_physical depășește num_pages_virtual
        if (num_pages_physical > num_pages_virtual) {
            logger.warn("Numărul de pagini fizice depășește numărul de pagini virtuale.");
            model.addAttribute("error", "Numărul de pagini fizice nu poate fi mai mare decât numărul de pagini virtuale.");
            return "index";
        }

        // Generăm tabelul de pagini virtuale (mv_pg, v_idx, start_addr, end_addr, mo_pg)
        List<PageEntry> table = new ArrayList<>();
        for (int i = 0; i < num_pages_virtual; i++) {
            int start_addr = i * page_size_bytes;
            int end_addr = start_addr + page_size_bytes - 1;
            table.add(new PageEntry(null, i, start_addr, end_addr, null));
        }

        model.addAttribute("MV_kb", MV_kb);
        model.addAttribute("MO_kb", MO_kb);
        model.addAttribute("page_kb", page_kb);
        model.addAttribute("page_size_bytes", page_size_bytes);
        model.addAttribute("num_pages_virtual", num_pages_virtual);
        model.addAttribute("num_pages_physical", num_pages_physical);
        model.addAttribute("table", table);

        logger.info("Navigating to pages_input.html with model attributes.");

        return "pages_input";
    }

    @PostMapping("/compute")
    public String compute(@RequestParam("MV_kb") int MV_kb,
                          @RequestParam("MO_kb") int MO_kb,
                          @RequestParam("page_kb") int page_kb,
                          @RequestParam("num_pages_virtual") int num_pages_virtual,
                          @RequestParam("num_pages_physical") int num_pages_physical,
                          @RequestParam("address") String addressStr,
                          @RequestParam Map<String, String> allParams,
                          Model model) {

        logger.info("Starting compute with MV_kb: {}, MO_kb: {}, page_kb: {}, num_pages_virtual: {}, num_pages_physical: {}, addressStr: {}",
                MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, addressStr);

        int page_size_bytes = page_kb * 1024;

        // Reconstruim tabelul inițial
        List<PageEntry> table = new ArrayList<>();
        for (int i = 0; i < num_pages_virtual; i++) {
            int start_addr = i * page_size_bytes;
            int end_addr = start_addr + page_size_bytes - 1;
            table.add(new PageEntry(null, i, start_addr, end_addr, null));
        }

        // Preluam MV_Page
        for (int i = 0; i < num_pages_virtual; i++) {
            String mv_page_str = allParams.get("mv_page_" + i);
            Integer mv_page_num = null;
            try {
                mv_page_num = Integer.valueOf(mv_page_str);
                logger.info("Setting MV_Page for virtual page {}: {}", i, mv_page_num);
            } catch (NumberFormatException e) {
                logger.error("Invalid MV_Page input for virtual page {}: {}", i, e.getMessage());
                model.addAttribute("error", "MV_Page trebuie să fie număr întreg!");
                populateModelForPagesInput(model, MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, table);
                return "pages_input";
            }
            PageEntry old = table.get(i);
            table.set(i, new PageEntry(mv_page_num, old.getV_idx(), old.getStart_addr(), old.getEnd_addr(), old.getMo_pg()));
        }

        // Preluam MO_Page
        for (int i = 0; i < num_pages_physical; i++) {
            String mo_page_str = allParams.get("mo_page_" + i);
            Integer mo_page_num = null;
            try {
                mo_page_num = Integer.valueOf(mo_page_str);
                logger.info("Setting MO_Page for physical page {}: {}", i, mo_page_num);
            } catch (NumberFormatException e) {
                logger.error("Invalid MO_Page input for physical page {}: {}", i, e.getMessage());
                model.addAttribute("error", "MO_Page trebuie să fie număr întreg!");
                populateModelForPagesInput(model, MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, table);
                return "pages_input";
            }
            PageEntry old = table.get(i);
            table.set(i, new PageEntry(old.getMv_pg(), old.getV_idx(), old.getStart_addr(), old.getEnd_addr(), mo_page_num));
        }

        // Restul paginilor nu au MO_Page
        for (int i = num_pages_physical; i < num_pages_virtual; i++) {
            PageEntry old = table.get(i);
            table.set(i, new PageEntry(old.getMv_pg(), old.getV_idx(), old.getStart_addr(), old.getEnd_addr(), null));
        }

        // Adresa virtuală
        int address;
        try {
            address = Integer.parseInt(addressStr);
            logger.info("Parsed address: {}", address);
        } catch (NumberFormatException e) {
            logger.error("Invalid address input: {}", e.getMessage());
            model.addAttribute("error", "Adresa trebuie să fie număr întreg!");
            populateModelForPagesInput(model, MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, table);
            return "pages_input";
        }

        Integer virtual_page_index = null;
        Integer page_start_addr = null;
        for (PageEntry p : table) {
            if (address >= p.getStart_addr() && address <= p.getEnd_addr()) {
                virtual_page_index = p.getV_idx();
                page_start_addr = p.getStart_addr();
                logger.info("Address {} found in virtual page {} starting at {}", address, virtual_page_index, page_start_addr);
                break;
            }
        }

        if (virtual_page_index == null) {
            logger.warn("Address {} nu se află în spațiul virtual!", address);
            model.addAttribute("error", "Adresa nu se află în spațiul virtual!");
            populateModelForPagesInput(model, MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, table);
            return "pages_input";
        }

        // Deplasare
        int displacement = address - page_start_addr;
        logger.info("Computed displacement: {}", displacement);

        // Determinăm paginile MV
        Integer mv_page_num = null;
        for (PageEntry p : table) {
            if (p.getV_idx().equals(virtual_page_index)) {
                mv_page_num = p.getMv_pg();
                break;
            }
        }

        // Verificăm dacă mv_page_num este null
        if (mv_page_num == null) {
            logger.warn("Pagina virtuală selectată nu are o mapare MV_Page.");
            model.addAttribute("error", "Pagina virtuală selectată nu are o mapare MV_Page.");
            populateModelForPagesInput(model, MV_kb, MO_kb, page_kb, num_pages_virtual, num_pages_physical, table);
            return "pages_input";
        }

        // Calcul adresa MV
        int mv_address = mv_page_num * page_size_bytes + displacement;
        logger.info("Calculated MV address: {}", mv_address);

        // Calcul adresa MO
        Integer mo_address = null;
        for (PageEntry p : table) {
            if (p.getMo_pg() != null && p.getMo_pg().equals(virtual_page_index)) {
                mo_address = p.getStart_addr() + displacement;
                logger.info("Calculated MO address: {}", mo_address);
                break;
            }
        }

        // Dacă nu am găsit o pagină fizică asociată, adresa MO nu poate fi calculată
        if (mo_address == null) {
            logger.info("No MO_Page mapping found for virtual page index {}. MO address cannot be calculated.", virtual_page_index);
        }

        model.addAttribute("table", table);
        model.addAttribute("address", address);
        model.addAttribute("virtual_page_index", virtual_page_index);
        model.addAttribute("displacement", displacement);
        model.addAttribute("mv_address", mv_address);
        model.addAttribute("mo_address", mo_address != null ? mo_address : "Nu există mapare MO");

        logger.info("Navigating to result.html with computed values.");

        return "result";
    }

    private void populateModelForPagesInput(Model model, int MV_kb, int MO_kb, int page_kb,
                                           int num_pages_virtual, int num_pages_physical, List<PageEntry> table) {
        model.addAttribute("MV_kb", MV_kb);
        model.addAttribute("MO_kb", MO_kb);
        model.addAttribute("page_kb", page_kb);
        model.addAttribute("page_size_bytes", page_kb * 1024);
        model.addAttribute("num_pages_virtual", num_pages_virtual);
        model.addAttribute("num_pages_physical", num_pages_physical);
        model.addAttribute("table", table);
    }

    // Clasă internă pentru stocarea informațiilor unei pagini
    public static class PageEntry {
        private Integer mv_pg;
        private Integer v_idx;
        private Integer start_addr;
        private Integer end_addr;
        private Integer mo_pg;

        public PageEntry(Integer mv_pg, Integer v_idx, Integer start_addr, Integer end_addr, Integer mo_pg) {
            this.mv_pg = mv_pg;
            this.v_idx = v_idx;
            this.start_addr = start_addr;
            this.end_addr = end_addr;
            this.mo_pg = mo_pg;
        }

        // Getteri și Setter pentru Thymeleaf
        public Integer getMv_pg() {
            return mv_pg;
        }

        public void setMv_pg(Integer mv_pg) {
            this.mv_pg = mv_pg;
        }

        public Integer getV_idx() {
            return v_idx;
        }

        public void setV_idx(Integer v_idx) {
            this.v_idx = v_idx;
        }

        public Integer getStart_addr() {
            return start_addr;
        }

        public void setStart_addr(Integer start_addr) {
            this.start_addr = start_addr;
        }

        public Integer getEnd_addr() {
            return end_addr;
        }

        public void setEnd_addr(Integer end_addr) {
            this.end_addr = end_addr;
        }

        public Integer getMo_pg() {
            return mo_pg;
        }

        public void setMo_pg(Integer mo_pg) {
            this.mo_pg = mo_pg;
        }
    }
}
