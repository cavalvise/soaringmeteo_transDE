import { ForecastMetadata } from "./Forecast";

// We show three forecast periods per day: morning, noon, and afternoon
export const periodsPerDay = 3;

// TODO Return an array containing an array for each day instead of a flat array
export const forecastOffsets = (gfsRunDateTime: Date, firstPeriodOffset: number, forecastMetadata: ForecastMetadata): Array<[number, Date]> => {
  // UTC-offsets of the periods we are interested in in a day (e.g., [9, 12, 15] around longitude 0)
  const forecastUTCOffsets = Array.from({ length: periodsPerDay }, (_, i) => firstPeriodOffset + i * 3);
  return Array.from<unknown, [number, number]>({ length: (forecastMetadata.latest / 3) - 1 }, (_, i) => [gfsRunDateTime.getUTCHours() + (i + 1) * 3, (i + 1) * 3])
    .filter(([utcOffset, _]) => forecastUTCOffsets.includes(utcOffset % 24))
    .map(([utcOffset, gfsOffset]) => {
      const date = new Date(gfsRunDateTime);
      date.setUTCHours(utcOffset);
      return [gfsOffset, date]
    })
};