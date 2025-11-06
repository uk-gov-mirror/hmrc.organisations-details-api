/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.organisationsdetailsapi.config

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.organisationsdetailsapi.backgroundjob.UpdateCacheTTLService
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider

class ConfigModule(environment: Environment, configuration: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    val delay = configuration.getOptional[Int]("retryDelay").getOrElse(1000)

    bindConstant().annotatedWith(Names.named("retryDelay")).to(delay)

    bind(classOf[HttpClientV2]).toProvider(classOf[HttpClientV2Provider])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[UpdateCacheTTLService]).asEagerSingleton()
  }
}
