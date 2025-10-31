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

package uk.gov.hmrc.organisationsdetailsapi.backgroundjob

import com.mongodb.ErrorCategory
import org.bson.BsonType

import javax.inject.{Inject, Singleton}
import org.mongodb.scala._
import org.mongodb.scala.model._
import org.mongodb.scala.bson._
import play.api.Logging
import uk.gov.hmrc.organisationsdetailsapi.cache.CacheRepositoryConfiguration
import uk.gov.hmrc.mongo.MongoComponent

import scala.concurrent.{ExecutionContext, Future}
import java.util.Date

@Singleton
class UpdateCacheTTLService @Inject() (
      mongo: MongoComponent,
      val cacheConfig: CacheRepositoryConfiguration
    )(implicit val ec: ExecutionContext)
  extends Logging {

  private val collection: MongoCollection[Document] =
    mongo.database.getCollection(cacheConfig.collName)

  private val lockCollection: MongoCollection[Document] =
    mongo.database.getCollection("organisations-details-cache-locks")

  private val lockId = "update-cache-ttl-lock"

  logger.info("Starting background job")
  // Trigger at the time of Startup
  updateItem()

  private def acquireLock(): Future[Boolean] = {
    val lockDoc = Document("_id" -> lockId, "createdAt" -> new Date())
    lockCollection.insertOne(lockDoc).toFuture().map(_ => true).recover {
      case ex: MongoWriteException if ex.getError.getCategory == ErrorCategory.DUPLICATE_KEY =>
        logger.info("Lock already exists. Skipping background job.")
        false
      case ex =>
        logger.error("Unexpected error while acquiring lock", ex)
        false
    }
  }

  private def updateItem(): Future[Unit] =
    acquireLock().flatMap {
      case true =>
        logger.info("Lock acquired. Starting aggregation-based update.")
        val lastUpdatedFilter = Filters.`type`("modifiedDetails.lastUpdated", BsonType.STRING)
        val createdATFilter = Filters.`type`("modifiedDetails.createdAt", BsonType.STRING)

        val updatePipeline = List(
          Document(
            "$set" -> Document(
              "modifiedDetails.lastUpdated" -> Document("$toDate" -> "$modifiedDetails.lastUpdated"),
              "modifiedDetails.createdAt"   -> Document("$toDate" -> "$modifiedDetails.createdAt")
            )
          )
        )

        collection
          .updateMany(
            Filters.and(
              lastUpdatedFilter,
              createdATFilter
            ),
            updatePipeline
          )
          .toFuture()
          .map { result =>
            logger.info(s"Background job to aggregation based update completed: ${result.getModifiedCount} documents updated.")
          }
          .recover { case ex =>
            logger.error("Background job to aggregation based update failed.", ex)
          }

      case false =>
        Future.successful(())
    }
}