package com.monicalab.page.config;

import com.monicalab.page.service.PageService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PageInitializer implements ApplicationRunner {

    private final PageService pageService;

    public PageInitializer(PageService pageService) {
        this.pageService = pageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        pageService.initializeFixedPages();
    }
}
