import logging
import time
from typing import Any

import httpx

from app.config import safe_search_log, search_diagnostics_enabled
from app.schemas import PoiSearchArguments, PoiSearchResult


POI_SEARCH_TIMEOUT_SECONDS = 30.0


class PoiSearchError(RuntimeError):
    """Raised when the Spring POI search API cannot provide a valid result."""


class PoiSearchTool:
    def __init__(
        self,
        spring_base_url: str,
        client: httpx.Client | None = None,
    ) -> None:
        self._client = client or httpx.Client(
            base_url=spring_base_url.rstrip("/"),
            timeout=POI_SEARCH_TIMEOUT_SECONDS,
        )

    def search(self, raw_arguments: dict[str, Any]) -> dict[str, Any]:
        trace = f"{id(raw_arguments):x}"
        try:
            arguments = PoiSearchArguments.model_validate(raw_arguments)
        except ValueError as exception:
            if search_diagnostics_enabled():
                logging.getLogger("uvicorn.error").exception(
                    "[poi-search:%s] argument_validation=failed exception=%s arguments=%s",
                    trace,
                    type(exception).__name__,
                    safe_search_log(raw_arguments),
                )
            raise
        params = {
            key: value
            for key, value in arguments.model_dump().items()
            if value is not None
        }
        path = "/api/map/pois/search"
        request_url = f"{str(self._client.base_url).rstrip('/')}{path}"
        started_at = time.perf_counter()
        response: httpx.Response | None = None

        if search_diagnostics_enabled():
            logging.getLogger("uvicorn.error").info(
                "[poi-search:%s] spring_request url=%s path=%s params=%s",
                trace,
                safe_search_log(request_url),
                path,
                safe_search_log(params),
            )

        try:
            response = self._client.get(path, params=params)
            elapsed_ms = (time.perf_counter() - started_at) * 1000
            if search_diagnostics_enabled():
                logging.getLogger("uvicorn.error").info(
                    "[poi-search:%s] spring_response status=%s elapsed_ms=%.1f",
                    trace,
                    response.status_code,
                    elapsed_ms,
                )
                if response.is_error:
                    logging.getLogger("uvicorn.error").warning(
                        "[poi-search:%s] spring_error_body=%s",
                        trace,
                        safe_search_log(response.text[:500]),
                    )
            response.raise_for_status()
            response_payload = response.json()
            try:
                result = PoiSearchResult.model_validate(response_payload)
            except ValueError as exception:
                if search_diagnostics_enabled():
                    raw_pois = (
                        response_payload.get("pois")
                        if isinstance(response_payload, dict)
                        else None
                    )
                    logging.getLogger("uvicorn.error").exception(
                        "[poi-search:%s] schema_validation=failed exception=%s total_count=%s pois_count=%s",
                        trace,
                        type(exception).__name__,
                        response_payload.get("total_count")
                        if isinstance(response_payload, dict)
                        else None,
                        len(raw_pois) if isinstance(raw_pois, list) else None,
                    )
                raise
        except (httpx.HTTPError, ValueError) as exception:
            if search_diagnostics_enabled():
                logging.getLogger("uvicorn.error").exception(
                    "[poi-search:%s] request_failed exception=%s status=%s elapsed_ms=%.1f",
                    trace,
                    type(exception).__name__,
                    response.status_code if response is not None else None,
                    (time.perf_counter() - started_at) * 1000,
                )
            raise PoiSearchError("Spring POI search request failed") from exception

        if search_diagnostics_enabled():
            logging.getLogger("uvicorn.error").info(
                "[poi-search:%s] schema_validation=success total_count=%s pois_count=%s",
                trace,
                result.total_count,
                len(result.pois),
            )
        return result.model_dump()
