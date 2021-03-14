import h from 'solid-js/h'
import { insert, style } from 'solid-js/web'

import { initializeMap } from './Map';
import { CanvasLayer } from './CanvasLayer';
import { PeriodSelector } from './PeriodSelector';
import { ForecastLayer } from './ForecastLayer';
import { ForecastMetadata } from './data/ForecastMetadata';

export class App {

  periodSelector: PeriodSelector
  forecastLayer: ForecastLayer
  readonly canvas: CanvasLayer
  private readonly map: L.Map
  private readonly mapElement: HTMLElement
  forecastMetadata: ForecastMetadata

  constructor(readonly forecasts: Array<ForecastMetadata>, containerElement: HTMLElement) {
    style(containerElement, { display: 'flex', 'align-items': 'stretch', 'align-content': 'stretch' });
    // The map *must* be initialized before we call the other constructors
    // It *must* also be mounted before we initialize it
    this.mapElement = h('div', { style: { flex: 1 } });
    insert(containerElement, this.mapElement);

    const [canvas, map] = initializeMap(this.mapElement);
    this.canvas = canvas;
    this.map = map;

    this.forecastMetadata = forecasts[forecasts.length - 1];
    this.map.attributionControl.setPrefix(`Initialization: ${this.forecastMetadata.init.toLocaleString(undefined, { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })}`);

    this.periodSelector = new PeriodSelector(
      this.forecastMetadata,
      this.mapElement,
      {
        hourOffset: value => this.forecastLayer.updateForecast(this.forecastMetadata, value, this.canvas)
      }
    );
    this.forecastLayer = new ForecastLayer(
      this.mapElement,
      this.periodSelector.getDetailedView(),
      forecasts,
      this.forecastMetadata,
      this.periodSelector.getHourOffset(),
      this.canvas,
      {
        detailedView: value => { this.periodSelector.updateDetailedView(value) },
        forecast: (value) => { this.selectForecast(value) },
        renderer: () => { this.forecastLayer.updateForecast(this.forecastMetadata, this.periodSelector.getHourOffset(), this.canvas) }
      }
    );

    this.selectForecast(this.forecastMetadata);

    map.on('click', (e: L.LeafletMouseEvent) => {
      this.periodSelector.showDetailedView(e.latlng.lat, e.latlng.lng);
    });

    map.on('keydown', (e: any) => {
      const event = e.originalEvent as KeyboardEvent;
      if (event.key === 'Escape') {
        this.periodSelector.hideMeteogram();
      }
    });
  }

  selectForecast(forecastMetadata: ForecastMetadata): void {
    this.forecastMetadata = forecastMetadata;
    this.map.attributionControl.setPrefix(`Initialization: ${forecastMetadata.init.toLocaleString(undefined, { month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })}`);
    this.periodSelector.unmount();
    this.periodSelector = new PeriodSelector(
      this.forecastMetadata,
      this.mapElement,
      {
        hourOffset: value => this.forecastLayer.updateForecast(this.forecastMetadata, value, this.canvas)
      }
    );
    this.forecastLayer.updateForecast(this.forecastMetadata, this.periodSelector.getHourOffset(), this.canvas);
  }

}
