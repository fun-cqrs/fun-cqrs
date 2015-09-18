package shop.domain.service

import shop.api.AkkaModule

trait ServiceModule extends ProductModule
                            with CustomerModule
                            with AkkaModule
