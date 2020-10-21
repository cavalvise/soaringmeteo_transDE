package org.soaringmeteo.gfs

import org.slf4j.LoggerFactory
import org.soaringmeteo.gfs.Settings.gfsRootUrl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.util.Try

object DownloadGribFiles {

  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    run(
      targetDir = os.Path(args(0)),
      gfsRun = FindLatestRun.now()
    )
  }

  def run(targetDir: os.Path, gfsRun: GfsRun): Unit = {
    os.remove.all(targetDir)
    os.makeDir.all(targetDir)

    val allForecasts =
      for {
        area <- Settings.gfsDownloadAreas
        t    <- Settings.forecastHours
      } yield (area, t)

    // Beware: I’ve been banned for using a parallelism level of 8
    inParallel(4, allForecasts) { case (area, t) =>
      // For now, download the entire files. Eventually, we might want to use the GRIB Filter
      // system to select only the parameters and altitudes we are interested in.
      val file = f"gfs.t${gfsRun.initTimeString}z.pgrb2.0p25.f$t%03d"

      // This URL has been constructed by going to https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl,
      // and then selecting a GFS run, and then selecting the levels as well as the variables we are
      // interested in.
      val url = s"$gfsRootUrl?file=${file}&dir=%2F${gfsRun.dateDirectory}%2F${gfsRun.initTimeString}&lev_mean_sea_level=on&lev_0C_isotherm=on&lev_10_m_above_ground=on&lev_200_mb=on&lev_2_m_above_ground=on&lev_300_mb=on&lev_400_mb=on&lev_450_mb=on&lev_500_mb=on&lev_550_mb=on&lev_600_mb=on&lev_650_mb=on&lev_700_mb=on&lev_750_mb=on&lev_800_mb=on&lev_850_mb=on&lev_900_mb=on&lev_950_mb=on&lev_boundary_layer_cloud_layer=on&lev_convective_cloud_layer=on&lev_entire_atmosphere=on&lev_high_cloud_layer=on&lev_low_cloud_layer=on&lev_middle_cloud_layer=on&lev_planetary_boundary_layer=on&lev_surface=on&var_ACPCP=on&var_APCP=on&var_CAPE=on&var_CIN=on&var_DSWRF=on&var_HGT=on&var_HPBL=on&var_LHTFL=on&var_MSLET=on&var_RH=on&var_SHTFL=on&var_TCDC=on&var_TMP=on&var_UGRD=on&var_VGRD=on&var_WEASD=on&leftlon=${area.leftLongitude}&rightlon=${area.rightLongitude}&toplat=${area.topLatitude}&bottomlat=${area.bottomLatitude}&subregion="

      // In my experience, the `time` directory is created ~3 hours after the run initialization
      // But the grib files that we are interested are only available around 3 hours and 30 min after the run initialization
      val response = insist(maxAttempts = 10, delay = 3.minutes, url)
      val targetFile = gfsRun.fileName(t, area)
      os.write(targetDir / targetFile, response.data.array)
      logger.debug(s"Downloaded $targetFile")
    }
    val metadata = ForecastMetadata(gfsRun.initDateTime, Settings.forecastHours.last)
    os.write(
      targetDir / "forecast.json",
      ForecastMetadata.jsonCodec(metadata).noSpaces
    )
    // TEMP Simulate old soargfs script for downloading data
    // We can safely remove these lines after we drop support for old soargfs
    os.write(targetDir / "aDone.txt", "")
    os.write(targetDir / "bDone.txt", "")
    os.write(targetDir / "cDone.txt", "")
    // END TEMP
  }

  /**
   * Try to fetch `url` at most `maxAttempts` times, waiting `delay` between each attempt.
   */
  def insist(maxAttempts: Int, delay: Duration, url: String): requests.Response = concurrent.blocking {
    val errorOrSucessfulResponse =
      Try(requests.get(url, readTimeout = 1200000, check = false))
        .toEither
        .filterOrElse(_.statusCode == 200, new Exception("Unexpected status code"))
    errorOrSucessfulResponse match {
      case Right(response) => response
      case Left(error) =>
        if (maxAttempts <= 1) {
          logger.error(s"Failed to fetch $url.", error)
          throw new RuntimeException(s"Unable to fetch $url.")
        } else {
          val remainingAttempts = maxAttempts - 1
          logger.debug(s"Failed to fetch $url. Waiting ${delay.toSeconds} seconds… ($remainingAttempts remaining attempts)", error)
          Thread.sleep(delay.toMillis)
          insist(remainingAttempts, delay, url)
        }
    }
  }

  // Downloading is slow, but it seems that by starting several downloads in parallel
  // makes the whole process faster (up to 5x), although we always download from the
  // very same server... Maybe they limit the download rate per file, not per IP…?
  def inParallel[A, B](parallelism: Int, as: Seq[A])(f: A => B): Seq[B] =
    for {
      batch <- as.grouped(parallelism).toSeq
      b     <- Await.result(Future.traverse(batch)(a => Future(f(a))), 1.hour)
    } yield b

}
