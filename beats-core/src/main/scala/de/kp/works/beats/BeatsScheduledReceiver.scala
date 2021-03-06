package de.kp.works.beats

/**
 * Copyright (c) 2020 - 2022 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

abstract class BeatsScheduledReceiver(timeInterval:Int, timeUnit:TimeUnit=TimeUnit.MILLISECONDS,
                                      numThreads:Int = 1) extends BeatsLogging {

  private var executorService:ScheduledExecutorService = _

  def getWorker:Runnable

  def start():Unit = {

    try {

      executorService = Executors.newScheduledThreadPool(numThreads)
      executorService.scheduleAtFixedRate(getWorker, 0,
        timeInterval, timeUnit)

    } catch {
      case _:Throwable => executorService.shutdown()
    }

  }

  def stop():Unit = {

    executorService.shutdown()
    executorService.shutdownNow()

  }

}
