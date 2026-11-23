package com.dawnrock.ddd.adapter.out.persistence;

import com.dawnrock.ddd.application.UnitOfWork;
import com.dawnrock.ddd.application.UnitOfWorkContract;

class InMemoryUnitOfWorkTests extends UnitOfWorkContract {

    @Override
    protected UnitOfWork unitOfWork() {
        return new InMemoryUnitOfWork();
    }
}
