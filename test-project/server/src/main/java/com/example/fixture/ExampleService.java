package com.example.fixture;

import de.espirit.firstspirit.module.Service;

// Service interface lives in :server so the FirstSpirit server can load it from
// the server class loader. The concrete implementation lives on module scope.
public interface ExampleService extends Service<ExampleService> {
}
