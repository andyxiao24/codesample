package spring.circular.reference.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class AService {
    private BService bService;
    @Autowired
    private ApplicationContext applicationContext;
    @PostConstruct
    public void init() {
        bService = applicationContext.getBean(BService.class);
    }
    @Async
    public void testFun() {
    }
}
