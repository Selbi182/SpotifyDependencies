package spotify.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ParseException;

import se.michaelthelin.spotify.exceptions.detailed.BadRequestException;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.PagingCursorbased;
import se.michaelthelin.spotify.requests.IRequest;
import se.michaelthelin.spotify.requests.data.IPagingCursorbasedRequestBuilder;
import se.michaelthelin.spotify.requests.data.IPagingRequestBuilder;
import spotify.api.events.SpotifyApiException;
import spotify.util.SpotifyUtils;

public class SpotifyCall {

	protected static SpotifyApiManager spotifyApiManager;

	private final static long RETRY_TIMEOUT_429 = 1000;
	private final static long RETRY_TIMEOUT_GENERIC_ERROR = 10 * 1000;
	private final static int MAX_ATTEMPTS = 10;

	/**
	 * Utility class
	 */
	private SpotifyCall() {
	}

	/**
	 * Executes a single "greedy" Spotify Web API request, meaning that on potential
	 * <i>429 Too many requests</i> errors the request will be retried up to 10
	 * times until it succeeds. Any attempts will be delayed by the response body's
	 * given <code>retryAfter</code> parameter in seconds (with an extra second due
	 * to occasional inaccuracies with that value). Generic errors will be retried
	 * too.
	 * 
	 * @param <T>            return type (e.g. Album, Playlist...)
	 * @param <BT>           Builder (e.g. Album.Builder, Playlist.Builder...)
	 * @param requestBuilder the basic, not built request builder
	 * @return the result item
	 * @throws SpotifyApiException if request didn't complete within 10 attempts
	 */
	public static <T, BT extends IRequest.Builder<T, ?>> T execute(IRequest.Builder<T, BT> requestBuilder) throws SpotifyApiException {
		Exception finalException = null;

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				IRequest<T> builtRequest = requestBuilder.build();
				return builtRequest.execute();
			} catch (Exception ex) {
				try {
					finalException = ex;
					throw ex;
				} catch (ParseException | IOException e) {
					throw new SpotifyApiException(e);
				} catch (UnauthorizedException e) {
					String newAccessToken = spotifyApiManager.refresh();
					requestBuilder.setHeader("Authorization", "Bearer " + newAccessToken);
				} catch (TooManyRequestsException e) {
					int timeout = e.getRetryAfter();
					long sleepMs = (timeout * RETRY_TIMEOUT_429 * attempt) + RETRY_TIMEOUT_429;
					SpotifyUtils.sneakySleep(sleepMs);
				} catch (NotFoundException | BadRequestException | ForbiddenException e) {
					break;
				} catch (Exception e) {
					SpotifyUtils.sneakySleep(RETRY_TIMEOUT_GENERIC_ERROR);
				}
			}
		}

		finalException.printStackTrace();
		throw new SpotifyApiException(finalException);
	}

	/**
	 * Executes a paging-based Spotify Web API request. This process is done
	 * greedily, see {@link SpotifyCall#execute}.
	 * 
	 * @param <T>                  the injected return type
	 * @param <BT>                 the injected Builder
	 * @param pagingRequestBuilder the basic, not built request paging builder
	 * @return the fully exhausted list of result items
	 */
	public static <T, BT extends IRequest.Builder<Paging<T>, ?>> List<T> executePaging(IPagingRequestBuilder<T, BT> pagingRequestBuilder) throws SpotifyApiException {
		List<T> resultList = new ArrayList<>();
		Paging<T> paging = null;
		do {
			if (paging != null && paging.getNext() != null) {
				pagingRequestBuilder.offset(paging.getOffset() + paging.getLimit());
			}
			paging = execute(pagingRequestBuilder);
			SpotifyUtils.addToListIfNotBlank(paging.getItems(), resultList);
		} while (paging.getNext() != null);
		return resultList;
	}

	/**
	 * Executes a PagingCursor-based Spotify Web API request. This process is done
	 * greedily, see {@link SpotifyCall#execute}.
	 * 
	 * @param <T>                  the injected return type
	 * @param <BT>                 the injected Builder
	 * @param <A>                  the After type (currently only String is
	 *                             supported)
	 * @param pagingRequestBuilder the basic, not built request PagingCursor builder
	 * @return the fully exhausted list of result items
	 */
	@SuppressWarnings("unchecked")
	public static <T, A, BT extends IRequest.Builder<PagingCursorbased<T>, ?>> List<T> executePaging(IPagingCursorbasedRequestBuilder<T, A, BT> pagingRequestBuilder) throws SpotifyApiException {
		List<T> resultList = new ArrayList<>();
		PagingCursorbased<T> paging = null;
		do {
			if (paging != null && paging.getNext() != null) {
				String after = paging.getCursors()[0].getAfter();
				try {
					pagingRequestBuilder.after((A) after);
				} catch (ClassCastException e) {
					throw new UnsupportedOperationException("Cursor-based paging is currently only supported for String-based cursors!");
				}
			}
			paging = execute(pagingRequestBuilder);
			SpotifyUtils.addToListIfNotBlank(paging.getItems(), resultList);
		} while (paging.getNext() != null);
		return resultList;
	}
}
