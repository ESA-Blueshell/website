package net.blueshell.blogservice;

import net.blueshell.db.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;

@org.springframework.stereotype.Service
public class Service extends BaseModelService<Blog, Long, Repository> {

    @Autowired
    public Service(Repository repository) {
        super(repository);
    }
}
