package com.pronoide.ddd.adapter.out.persistence;

import com.pronoide.ddd.application.UnitOfWork;
import com.pronoide.ddd.application.UnitOfWorkContract;

class InMemoryUnitOfWorkTests extends UnitOfWorkContract {

    @Override
    protected UnitOfWork unitOfWork() {
        return new InMemoryUnitOfWork();
    }
}
